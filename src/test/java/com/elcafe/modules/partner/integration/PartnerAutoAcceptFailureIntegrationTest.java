package com.elcafe.modules.partner.integration;

import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The regression this pins is a transaction bug that unit tests structurally cannot see.
 *
 * <p>{@code OrderService.updateOrderStatus} is {@code @Transactional(REQUIRED)}, so when the partner
 * push called it for the auto-accept it <b>joined the push's own transaction</b>. Accepting an order
 * checks ingredient availability and legitimately throws when the kitchen is short — and Spring marks a
 * participating transaction rollback-only on that throw, which catching the exception does not undo. So
 * the push swallowed the failure, returned a cheerful response, and then blew up at commit with
 * {@code UnexpectedRollbackException}: a 500 to the partner, and the order, its items, its payment and
 * its correlation row all rolled back — <i>after</i> the kitchen ticket had already printed and the
 * staff notification had already gone out. The venue was left holding paper for an order that did not
 * exist, and the partner, following the documented retry contract, would re-push into the same wall.
 *
 * <p>A Mockito-based unit test asserts the opposite and passes, because a mocked {@code OrderService}
 * carries no transaction interceptor. Only a real context with a real transaction manager shows it,
 * which is what this test is for. {@code InventoryService} is mocked to force the accept to fail, since
 * that is the realistic trigger and seeding a short recipe would only obscure the point.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:partneracceptfailit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        // Auto-accept ON: the failing path is the default one.
        "app.partner.auto-accept=true",
})
class PartnerAutoAcceptFailureIntegrationTest {

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

    /** Forced to refuse the accept, standing in for a kitchen that is short an ingredient. */
    @MockBean private InventoryService inventoryService;

    private Long restaurantId;
    private Long productId;
    private String apiKey;

    @BeforeAll
    void seed() {
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Accept Fail Cafe").address("3 Test St")
                .active(true).acceptingOrders(true).build());
        restaurantId = restaurant.getId();

        Category category = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Main").sortOrder(0).active(true).build());
        productId = productRepository.save(Product.builder()
                .category(category).name("Osh").price(new BigDecimal("30000"))
                .status(ProductStatus.LIVE).inStock(true).build()).getId();

        apiKey = partnerAccessService.generateApiKey();
        Partner partner = partnerRepository.save(Partner.builder()
                .name("Accept Fail Aggregator").slug("accept-fail-agg")
                .apiKeyHash(partnerAccessService.hashApiKey(apiKey))
                .apiKeyPrefix(partnerAccessService.prefixOf(apiKey))
                .active(true).build());

        partnerRestaurantRepository.save(PartnerRestaurant.builder()
                .partnerId(partner.getId()).restaurantId(restaurantId)
                .canReadMenu(true).canPushOrders(true).active(true).build());
    }

    @Test
    @DisplayName("a failing auto-accept must not destroy the order — the ticket has already printed")
    void autoAcceptFailure_leavesOrderStanding() throws Exception {
        when(inventoryService.checkIngredientAvailability(any())).thenReturn(false);

        String body = mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":" + restaurantId + ",\"externalOrderId\":\"EXT-ACCEPT-FAIL\","
                                + "\"orderType\":\"TAKEAWAY\",\"paymentMode\":\"PREPAID\","
                                + "\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        long orderId = data.path("orderId").asLong();

        // It stays at NEW for a human to accept, which is the documented behaviour.
        assertThat(data.path("status").asText()).isEqualTo("NEW");

        Order persisted = orderRepository.findById(orderId).orElse(null);
        assertThat(persisted)
                .as("the order must survive a refused accept — its ticket is already on the pass")
                .isNotNull();
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.NEW);

        assertThat(partnerOrderRepository.findByOrderId(orderId))
                .as("the correlation row must survive too, or a retry would create a second order")
                .isPresent();
    }
}
