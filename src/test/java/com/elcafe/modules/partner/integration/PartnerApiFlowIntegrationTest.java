package com.elcafe.modules.partner.integration;

import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.repository.PartnerOrderRepository;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import com.elcafe.modules.partner.service.PartnerAccessService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the whole V187 partner integration over real HTTP through the ACTUAL production security
 * filter chain — the same path a delivery aggregator's requests take.
 *
 * <p>The seams it proves, which the unit tests mock away: an {@code X-Partner-Key} authenticates
 * through {@link com.elcafe.security.PartnerApiKeyFilter} and clears the {@code ROLE_PARTNER} rule; a
 * missing key is a 401 and a key without a grant for that venue is a 403 (so a valid credential does
 * not open the whole platform); a pushed order persists with {@code orderSource = AGGREGATOR} and a
 * {@code partner_orders} correlation row; a replay returns the first order instead of creating a
 * second; and a stale price is refused rather than silently repriced.
 *
 * <p>Auto-accept is deliberately ON here, because reaching the kitchen is the point of the feature.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:partnerapiflowit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        "app.partner.auto-accept=true",
})
class PartnerApiFlowIntegrationTest {

    private static final String KEY_HEADER = "X-Partner-Key";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private PartnerRestaurantRepository partnerRestaurantRepository;
    @Autowired private PartnerOrderRepository partnerOrderRepository;
    @Autowired private PartnerAccessService partnerAccessService;

    private Long grantedRestaurantId;
    private Long ungrantedRestaurantId;
    private Long secondRestaurantId;
    private Long secondProductId;
    private Long productId;
    private String apiKey;

    private final BigDecimal price = new BigDecimal("30000");

    @BeforeAll
    void seed() {
        Restaurant granted = restaurantRepository.save(Restaurant.builder()
                .name("Partner Cafe").address("1 Test St")
                .active(true).acceptingOrders(true).deliveryFee(new BigDecimal("5000")).build());
        grantedRestaurantId = granted.getId();

        // A second venue the partner is NOT granted, so "valid key ≠ access" is testable.
        ungrantedRestaurantId = restaurantRepository.save(Restaurant.builder()
                .name("Other Cafe").address("2 Test St")
                .active(true).acceptingOrders(true).build()).getId();

        Category category = categoryRepository.save(Category.builder()
                .restaurant(granted).name("Main").sortOrder(0).active(true).build());
        productId = productRepository.save(Product.builder()
                .category(category).name("Osh").price(price)
                .status(ProductStatus.LIVE).inStock(true).build()).getId();

        // Mint a key the same way the admin endpoint does: store only the hash.
        apiKey = partnerAccessService.generateApiKey();
        Partner partner = partnerRepository.save(Partner.builder()
                .name("Test Aggregator").slug("test-agg")
                .apiKeyHash(partnerAccessService.hashApiKey(apiKey))
                .apiKeyPrefix(partnerAccessService.prefixOf(apiKey))
                .active(true).build());

        partnerRestaurantRepository.save(PartnerRestaurant.builder()
                .partnerId(partner.getId()).restaurantId(grantedRestaurantId)
                .canReadMenu(true).canPushOrders(true).active(true).build());

        // A third venue the partner IS granted, used to prove dedupe is scoped per venue.
        Restaurant second = restaurantRepository.save(Restaurant.builder()
                .name("Second Cafe").address("4 Test St")
                .active(true).acceptingOrders(true).build());
        secondRestaurantId = second.getId();
        Category secondCategory = categoryRepository.save(Category.builder()
                .restaurant(second).name("Main").sortOrder(0).active(true).build());
        secondProductId = productRepository.save(Product.builder()
                .category(secondCategory).name("Lagman").price(price)
                .status(ProductStatus.LIVE).inStock(true).build()).getId();
        partnerRestaurantRepository.save(PartnerRestaurant.builder()
                .partnerId(partner.getId()).restaurantId(secondRestaurantId)
                .canReadMenu(true).canPushOrders(true).active(true).build());
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("no key → 401; the partner subtree is closed to anonymous callers")
    void menuWithoutKey_isUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/partner/menu/" + grantedRestaurantId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("an unknown key → 401, indistinguishable from presenting none")
    void menuWithBadKey_isUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/partner/menu/" + grantedRestaurantId)
                        .header(KEY_HEADER, "elc_not_a_real_key_at_all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("a granted venue's menu comes back with prices and availability")
    void menuWithKey_returnsMenu() throws Exception {
        String body = mvc.perform(get("/api/v1/partner/menu/" + grantedRestaurantId)
                        .header(KEY_HEADER, apiKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.path("restaurantId").asLong()).isEqualTo(grantedRestaurantId);
        assertThat(data.path("acceptingOrders").asBoolean()).isTrue();

        JsonNode product = data.path("categories").get(0).path("products").get(0);
        assertThat(product.path("id").asLong()).isEqualTo(productId);
        assertThat(new BigDecimal(product.path("price").asText())).isEqualByComparingTo(price);
        // Present with a flag rather than dropped, so a partner can tell sold-out from delisted.
        assertThat(product.path("available").asBoolean()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("a valid key does NOT open a venue it was never granted (403)")
    void menuForUngrantedVenue_isForbidden() throws Exception {
        mvc.perform(get("/api/v1/partner/menu/" + ungrantedRestaurantId)
                        .header(KEY_HEADER, apiKey))
                .andExpect(status().isForbidden());
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("a pushed order is created as AGGREGATOR, auto-accepted, and correlated")
    void pushOrder_createsAggregatorOrder() throws Exception {
        String body = mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("EXT-100", 2, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.path("duplicate").asBoolean()).isFalse();
        assertThat(new BigDecimal(data.path("total").asText())).isEqualByComparingTo("60000");

        Order order = orderRepository.findById(data.path("orderId").asLong()).orElseThrow();
        assertThat(order.getOrderSource()).isEqualTo(OrderSource.AGGREGATOR);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        // The ticket leads with who sent it — the first thing the counter needs.
        assertThat(order.getCustomerNotes()).contains("Test Aggregator").contains("EXT-100");

        assertThat(partnerOrderRepository.findByOrderId(order.getId()))
                .as("a correlation row must exist so support can answer 'which of their orders is this'")
                .isPresent();
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("re-pushing the same external id returns the original order, not a second one")
    void pushOrder_replay_isIdempotent() throws Exception {
        long ordersBefore = orderRepository.count();

        String body = mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("EXT-100", 2, null)))
                // 200, not 201: nothing was created.
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.path("duplicate").asBoolean()).isTrue();
        assertThat(orderRepository.count())
                .as("a retry must not cook the same lunch twice")
                .isEqualTo(ordersBefore);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("an item that is not on this menu → 422 with the offending id")
    void pushOrder_unknownProduct_isUnprocessable() throws Exception {
        String body = "{\"restaurantId\":" + grantedRestaurantId + ",\"externalOrderId\":\"EXT-404\","
                + "\"orderType\":\"TAKEAWAY\",\"paymentMode\":\"PREPAID\","
                + "\"items\":[{\"productId\":987654,\"quantity\":1}]}";

        String response = mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        JsonNode errors = objectMapper.readTree(response).path("errors");
        assertThat(errors.path("reason").asText()).isEqualTo("UNKNOWN_ITEMS");
        assertThat(errors.path("unknownProductIds").get(0).asLong()).isEqualTo(987654L);
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("a stale price → 409 carrying both totals, and no order is created")
    void pushOrder_priceMismatch_isConflict() throws Exception {
        long ordersBefore = orderRepository.count();

        String response = mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        // Selling at a stale 25000 each; our menu says 30000.
                        .content(orderJson("EXT-STALE", 2, "50000")))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        JsonNode errors = objectMapper.readTree(response).path("errors");
        assertThat(errors.path("reason").asText()).isEqualTo("PRICE_MISMATCH");
        assertThat(new BigDecimal(errors.path("actualTotal").asText())).isEqualByComparingTo("60000");
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("a partner can poll back its own order by its own id")
    void getOrder_byExternalId() throws Exception {
        String body = mvc.perform(get("/api/v1/partner/orders/EXT-100").param("restaurantId", String.valueOf(grantedRestaurantId))
                        .header(KEY_HEADER, apiKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.path("externalOrderId").asText()).isEqualTo("EXT-100");
        assertThat(data.path("status").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("deactivating the partner kills every venue at once")
    void deactivatedPartner_isLockedOut() throws Exception {
        Partner partner = partnerRepository.findBySlug("test-agg").orElseThrow();
        partner.setActive(false);
        partnerRepository.save(partner);

        mvc.perform(get("/api/v1/partner/menu/" + grantedRestaurantId).header(KEY_HEADER, apiKey))
                .andExpect(status().isUnauthorized());

        // Restore, so ordering between this class's tests stays independent of run order elsewhere.
        partner.setActive(true);
        partnerRepository.save(partner);
        Optional<Partner> restored = partnerRepository.findBySlug("test-agg");
        assertThat(restored).isPresent();
        assertThat(restored.get().getActive()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("the same external id at a DIFFERENT venue is a new order, not a duplicate")
    void sameExternalIdAtAnotherVenue_createsItsOwnOrder() throws Exception {
        // Aggregators commonly number orders per store, so "1001" legitimately exists at each venue.
        // Keying dedupe on (partner, external id) alone made the second venue's order look like a
        // replay of the first and dropped it silently — a customer waiting for food nobody was cooking.
        String body = mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":" + secondRestaurantId
                                + ",\"externalOrderId\":\"EXT-100\","
                                + "\"orderType\":\"TAKEAWAY\",\"paymentMode\":\"PREPAID\","
                                + "\"items\":[{\"productId\":" + secondProductId + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.path("duplicate").asBoolean())
                .as("a different venue's order must never be mistaken for a replay")
                .isFalse();

        Order order = orderRepository.findById(data.path("orderId").asLong()).orElseThrow();
        assertThat(order.getRestaurant().getId()).isEqualTo(secondRestaurantId);
    }

    private String orderJson(String externalId, int quantity, String expectedTotal) {
        StringBuilder json = new StringBuilder()
                .append("{\"restaurantId\":").append(grantedRestaurantId)
                .append(",\"externalOrderId\":\"").append(externalId).append('"')
                .append(",\"orderType\":\"TAKEAWAY\",\"paymentMode\":\"PREPAID\"")
                .append(",\"customer\":{\"name\":\"Ali\",\"phone\":\"+998901112233\"}")
                .append(",\"items\":[{\"productId\":").append(productId)
                .append(",\"quantity\":").append(quantity).append("}]");
        if (expectedTotal != null) {
            json.append(",\"expectedTotal\":").append(expectedTotal);
        }
        return json.append('}').toString();
    }
}
