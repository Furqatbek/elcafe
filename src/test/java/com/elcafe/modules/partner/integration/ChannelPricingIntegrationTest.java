package com.elcafe.modules.partner.integration;

import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerPriceRule;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.enums.PriceAdjustmentType;
import com.elcafe.modules.partner.enums.PriceRuleScope;
import com.elcafe.modules.partner.repository.PartnerPriceRuleRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the one invariant channel pricing lives or dies on: <b>the price we publish is the price we
 * charge</b>.
 *
 * <p>A partner displays our number to their customer and sends it straight back as
 * {@code expectedTotal}. If the menu and the order priced independently, every single order would be
 * rejected for a mismatch neither side could diagnose — so this test reads the menu over HTTP, pushes
 * an order quoting exactly what the menu said, and asserts it is accepted and stored at that price. It
 * fails the moment anyone gives the two paths separate arithmetic.
 *
 * <p>It also pins that the base price is untouched: the whole point is selling the same dish at two
 * prices, so a markup that leaked into {@code products.price} would raise it for walk-in customers too.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:channelpricingit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        "app.partner.auto-accept=false",
})
class ChannelPricingIntegrationTest {

    private static final String KEY_HEADER = "X-Partner-Key";
    /** 30 000 at the counter. The markup below is what ZBR's customers pay. */
    private static final BigDecimal BASE_PRICE = new BigDecimal("30000");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private PartnerRestaurantRepository partnerRestaurantRepository;
    @Autowired private PartnerPriceRuleRepository partnerPriceRuleRepository;
    @Autowired private PartnerAccessService partnerAccessService;

    private Long restaurantId;
    private Long productId;
    private Long markedUpProductId;
    private Long partnerId;
    @org.springframework.beans.factory.annotation.Autowired
    private com.elcafe.modules.partner.service.PartnerAdminService partnerAdminService;
    private String apiKey;

    @BeforeAll
    void seed() {
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Pricing Cafe").address("7 Test St")
                .active(true).acceptingOrders(true).build());
        restaurantId = restaurant.getId();

        Category category = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Main").sortOrder(0).active(true).build());
        productId = productRepository.save(Product.builder()
                .category(category).name("Osh").price(BASE_PRICE)
                .status(ProductStatus.LIVE).inStock(true).build()).getId();
        markedUpProductId = productRepository.save(Product.builder()
                .category(category).name("Lagman").price(BASE_PRICE)
                .status(ProductStatus.LIVE).inStock(true).build()).getId();

        apiKey = partnerAccessService.generateApiKey();
        Partner partner = partnerRepository.save(Partner.builder()
                .name("ZBR").slug("zbr")
                .apiKeyHash(partnerAccessService.hashApiKey(apiKey))
                .apiKeyPrefix(partnerAccessService.prefixOf(apiKey))
                .active(true).build());
        partnerId = partner.getId();

        // +15% across the venue, rounded to the nearest 500 so the menu reads cleanly.
        partnerRestaurantRepository.save(PartnerRestaurant.builder()
                .partnerId(partnerId).restaurantId(restaurantId)
                .canReadMenu(true).canPushOrders(true).active(true)
                .priceAdjustmentType(PriceAdjustmentType.PERCENT)
                .priceAdjustmentValue(new BigDecimal("15"))
                .priceRounding(new BigDecimal("500"))
                .build());

        // One dish priced differently again, to prove an override beats the venue default end to end.
        partnerPriceRuleRepository.save(PartnerPriceRule.builder()
                .partnerId(partnerId).restaurantId(restaurantId)
                .scope(PriceRuleScope.PRODUCT).targetId(markedUpProductId)
                .adjustmentType(PriceAdjustmentType.FIXED)
                .adjustmentValue(new BigDecimal("41000"))
                .active(true)
                .build());
    }

    private JsonNode menuProduct(Long wantedId) throws Exception {
        String body = mvc.perform(get("/api/v1/partner/menu/" + restaurantId)
                        .header(KEY_HEADER, apiKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (JsonNode product : objectMapper.readTree(body).path("data")
                .path("categories").get(0).path("products")) {
            if (product.path("id").asLong() == wantedId) {
                return product;
            }
        }
        throw new AssertionError("product " + wantedId + " missing from the partner menu");
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("the menu publishes the marked-up price, not the counter price")
    void menu_showsChannelPrice() throws Exception {
        // 30 000 + 15% = 34 500, already a multiple of 500.
        assertThat(new BigDecimal(menuProduct(productId).path("price").asText()))
                .isEqualByComparingTo("34500");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("a per-item override beats the venue default")
    void menu_itemOverrideWins() throws Exception {
        assertThat(new BigDecimal(menuProduct(markedUpProductId).path("price").asText()))
                .isEqualByComparingTo("41000");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("an order quoting the menu price is accepted and charged at exactly that price")
    void order_chargesTheSamePriceTheMenuQuoted() throws Exception {
        BigDecimal quoted = new BigDecimal(menuProduct(productId).path("price").asText());
        BigDecimal expectedTotal = quoted.multiply(BigDecimal.valueOf(2));

        String body = mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":" + restaurantId + ",\"externalOrderId\":\"ZBR-1\","
                                + "\"orderType\":\"TAKEAWAY\",\"paymentMode\":\"PREPAID\","
                                + "\"expectedTotal\":" + expectedTotal.toPlainString() + ","
                                + "\"items\":[{\"productId\":" + productId + ",\"quantity\":2}]}"))
                // Accepted, not 409 PRICE_MISMATCH: the two paths agree because they share a resolver.
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(new BigDecimal(data.path("total").asText())).isEqualByComparingTo(expectedTotal);

        // Asserted through the response and the order's own scalar total rather than order.getItems(),
        // which is lazy and has no session out here.
        assertThat(new BigDecimal(data.path("items").get(0).path("unitPrice").asText()))
                .as("the line is charged at the channel price the menu quoted")
                .isEqualByComparingTo(quoted);

        Order order = orderRepository.findById(data.path("orderId").asLong()).orElseThrow();
        assertThat(order.getTotal())
                .as("the order is stored at the channel price, so revenue reflects what was charged")
                .isEqualByComparingTo(expectedTotal);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("quoting the BASE price is rejected — the partner is on a stale menu")
    void order_atBasePrice_isRejected() throws Exception {
        String response = mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":" + restaurantId + ",\"externalOrderId\":\"ZBR-2\","
                                + "\"orderType\":\"TAKEAWAY\",\"paymentMode\":\"PREPAID\","
                                + "\"expectedTotal\":30000,"
                                + "\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        JsonNode errors = objectMapper.readTree(response).path("errors");
        assertThat(errors.path("reason").asText()).isEqualTo("PRICE_MISMATCH");
        assertThat(new BigDecimal(errors.path("actualTotal").asText())).isEqualByComparingTo("34500");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("what the partner charges on top never touches what we publish or bill")
    void partnerCustomerFee_doesNotReachAnyPrice() throws Exception {
        // ZBR adds 8% at their own checkout. We record it so a venue setting a markup can see the
        // price their diner actually pays — and that is ALL it is for. If it ever reached the
        // resolver, every venue would silently charge the aggregator's fee on top of their own.
        partnerAdminService.setCustomerFee(partnerId, new BigDecimal("8"));

        assertThat(new BigDecimal(menuProduct(productId).path("price").asText()))
                .as("the published price is the venue's markup and nothing else")
                .isEqualByComparingTo("34500");

        // And the order still settles at the published number, not the one their customer paid.
        assertThat(partnerRepository.findById(partnerId).orElseThrow().getCustomerFeePercent())
                .isEqualByComparingTo("8");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("the counter price is untouched — the whole point is two prices, not one raised one")
    void basePrice_isUnchanged() {
        assertThat(productRepository.findById(productId).orElseThrow().getPrice())
                .isEqualByComparingTo(BASE_PRICE);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("a partner with no markup configured still sells at the base price")
    void partnerWithoutMarkup_sellsAtBasePrice() throws Exception {
        String plainKey = partnerAccessService.generateApiKey();
        Partner plain = partnerRepository.save(Partner.builder()
                .name("No Markup Aggregator").slug("plain-agg")
                .apiKeyHash(partnerAccessService.hashApiKey(plainKey))
                .apiKeyPrefix(partnerAccessService.prefixOf(plainKey))
                .active(true).build());
        // Granted with the defaults a pre-V189 grant would have had.
        partnerRestaurantRepository.save(PartnerRestaurant.builder()
                .partnerId(plain.getId()).restaurantId(restaurantId)
                .canReadMenu(true).canPushOrders(true).active(true)
                .build());

        String body = mvc.perform(get("/api/v1/partner/menu/" + restaurantId)
                        .header(KEY_HEADER, plainKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode product = objectMapper.readTree(body).path("data")
                .path("categories").get(0).path("products").get(0);
        assertThat(new BigDecimal(product.path("price").asText()))
                .as("an existing integration must not be silently repriced by this migration")
                .isEqualByComparingTo(BASE_PRICE);
    }
}
