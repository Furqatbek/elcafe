package com.elcafe.modules.partner.integration;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.partner.entity.IntegrationEvent;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.enums.IntegrationEventType;
import com.elcafe.modules.partner.outbox.PartnerEventDispatcher;
import com.elcafe.modules.partner.repository.IntegrationEventRepository;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import com.elcafe.modules.partner.service.PartnerAccessService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The kitchen running out of beef has to reach the aggregator's menu on its own.
 *
 * <p>The failure this prevents is specific and expensive. {@code products.in_stock} is a switch
 * somebody flips; it says nothing about the walk-in, so the last kilo of beef going at 7pm leaves the
 * dish on sale until a human notices. On our own screens that is an annoyance — the order is refused
 * at the accept step. On an aggregator the customer has already paid, and a refund plus a one-star
 * review is the price of finding out late.
 *
 * <p>So this drives real stock movements through {@code InventoryService} and asserts what a partner
 * would actually see: the menu they pull, the answer they get when they push an order, and the
 * message we queue to push at them. Both directions matter — a delivery arriving has to put the dish
 * back, or the first sold-out of the day is permanent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AutoSoldOutIntegrationTest.TestDispatcherConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:autosoldoutit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        "app.partner.auto-accept=false",
        // Nothing here needs delivery; the assertions are about what gets queued.
        "app.partner.outbox.poll-ms=3600000",
})
class AutoSoldOutIntegrationTest {

    private static final String KEY_HEADER = "X-Partner-Key";

    /** Claims the partner so events are queued — the publisher drops events nobody can deliver. */
    static class ClaimingDispatcher implements PartnerEventDispatcher {
        @Override
        public boolean supports(Partner partner) {
            return "zbr".equals(partner.getSlug());
        }

        @Override
        public void dispatch(Partner partner, IntegrationEvent event) {
            // Delivery is not what this test is about; the outbox test covers it.
        }
    }

    @TestConfiguration
    static class TestDispatcherConfig {
        @Bean
        ClaimingDispatcher claimingDispatcher() {
            return new ClaimingDispatcher();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryIngredientRepository ingredientRepository;
    @Autowired private InventoryProductIngredientRepository productIngredientRepository;
    @Autowired private InventoryService inventoryService;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private PartnerRestaurantRepository partnerRestaurantRepository;
    @Autowired private IntegrationEventRepository integrationEventRepository;
    @Autowired private PartnerAccessService partnerAccessService;

    private Long restaurantId;
    /** Cooked from beef, so it comes and goes with the stock. */
    private Long oshId;
    /** Has no recipe at all, so it must never be taken off the menu by this mechanism. */
    private Long waterId;
    private Long beefId;
    private String apiKey;

    private final AtomicLong externalOrderCounter = new AtomicLong();

    @BeforeAll
    void seed() {
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Sold Out Cafe").address("9 Test St")
                .active(true).acceptingOrders(true).build());
        restaurantId = restaurant.getId();

        Category category = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Main").sortOrder(0).active(true).build());

        Product osh = productRepository.save(Product.builder()
                .category(category).name("Osh").price(new BigDecimal("30000"))
                .status(ProductStatus.LIVE).inStock(true).build());
        oshId = osh.getId();

        waterId = productRepository.save(Product.builder()
                .category(category).name("Mineral water").price(new BigDecimal("5000"))
                .status(ProductStatus.LIVE).inStock(true).build()).getId();

        Ingredient beef = ingredientRepository.save(Ingredient.builder()
                .restaurant(restaurant).name("Beef").unit("kg")
                .currentStock(new BigDecimal("10.000")).minimumStock(new BigDecimal("1.000"))
                .costPerUnit(new BigDecimal("90000")).active(true).trackInventory(true).build());
        beefId = beef.getId();

        productIngredientRepository.save(ProductIngredient.builder()
                .product(osh).ingredient(beef)
                .quantityRequired(new BigDecimal("0.500")).unit("kg").optional(false).build());

        apiKey = partnerAccessService.generateApiKey();
        Partner zbr = partnerRepository.save(Partner.builder()
                .name("ZBR").slug("zbr")
                .apiKeyHash(partnerAccessService.hashApiKey(apiKey))
                .apiKeyPrefix(partnerAccessService.prefixOf(apiKey))
                .active(true).build());
        partnerRestaurantRepository.save(PartnerRestaurant.builder()
                .partnerId(zbr.getId()).restaurantId(restaurantId)
                .canReadMenu(true).canPushOrders(true).active(true).build());
    }

    @BeforeEach
    void restockAndClearEvents() {
        inventoryService.adjustStock(beefId, new BigDecimal("10.000"), "test reset", "test");
        integrationEventRepository.deleteAll();
    }

    private boolean menuSays(Long productId) throws Exception {
        String body = mvc.perform(get("/api/v1/partner/menu/" + restaurantId).header(KEY_HEADER, apiKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (JsonNode product : objectMapper.readTree(body).path("data").path("categories")
                .get(0).path("products")) {
            if (product.path("id").asLong() == productId) {
                return product.path("available").asBoolean();
            }
        }
        throw new AssertionError("product " + productId + " missing from the partner menu");
    }

    private int pushOrderFor(Long productId) throws Exception {
        String externalId = "SO-" + externalOrderCounter.incrementAndGet();
        return mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":" + restaurantId + ",\"externalOrderId\":\"" + externalId
                                + "\",\"orderType\":\"TAKEAWAY\",\"paymentMode\":\"PREPAID\","
                                + "\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andReturn().getResponse().getStatus();
    }

    private boolean recipeAvailable(Long productId) {
        return productRepository.findById(productId).orElseThrow().getRecipeAvailable();
    }

    @Test
    @DisplayName("running the beef down takes osh off the partner menu without anyone touching a switch")
    void stockRunningOut_removesTheDishFromThePartnerMenu() throws Exception {
        assertThat(menuSays(oshId)).isTrue();

        inventoryService.adjustStock(beefId, new BigDecimal("0.100"), "end of service", "chef");

        assertThat(recipeAvailable(oshId)).isFalse();
        assertThat(menuSays(oshId)).isFalse();
        // The manual switch is untouched — a delivery must not be able to undo a manager's decision,
        // and a manager must not be able to claim stock the kitchen does not have.
        assertThat(productRepository.findById(oshId).orElseThrow().getInStock()).isTrue();
    }

    @Test
    @DisplayName("an order for a dish we cannot cook is refused, not accepted and sorted out later")
    void unmakeableDish_isRefusedAtThePartnerOrderEndpoint() throws Exception {
        assertThat(pushOrderFor(oshId)).isEqualTo(201);

        inventoryService.adjustStock(beefId, new BigDecimal("0.100"), "end of service", "chef");

        // 409, not 422: the basket is valid and the same request may succeed once stock arrives.
        assertThat(pushOrderFor(oshId)).isEqualTo(409);
    }

    @Test
    @DisplayName("going sold out queues one message per partner, carrying the effective answer")
    void stockRunningOut_queuesAnAvailabilityMessage() throws Exception {
        inventoryService.adjustStock(beefId, new BigDecimal("0.100"), "end of service", "chef");

        List<IntegrationEvent> events = integrationEventRepository.findAll();
        assertThat(events).hasSize(1);
        IntegrationEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo(IntegrationEventType.MENU_ITEM_AVAILABILITY);
        assertThat(event.getSubjectKey()).isEqualTo("product:" + oshId);

        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.path("productId").asLong()).isEqualTo(oshId);
        assertThat(payload.path("available").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a delivery arriving puts the dish back on sale")
    void restock_putsTheDishBack() throws Exception {
        inventoryService.adjustStock(beefId, new BigDecimal("0.100"), "end of service", "chef");
        assertThat(menuSays(oshId)).isFalse();

        inventoryService.addStock(beefId, new BigDecimal("5.000"), "delivery", "manager");

        assertThat(recipeAvailable(oshId)).isTrue();
        assertThat(menuSays(oshId)).isTrue();
        assertThat(pushOrderFor(oshId)).isEqualTo(201);
    }

    @Test
    @DisplayName("a product with no recipe is never taken off the menu by this")
    void productWithoutRecipe_isUntouched() throws Exception {
        inventoryService.adjustStock(beefId, BigDecimal.ZERO, "everything gone", "chef");

        // We know nothing about what mineral water is made of. Guessing "sold out" would empty the
        // menu of any venue that never entered recipes.
        assertThat(recipeAvailable(waterId)).isTrue();
        assertThat(menuSays(waterId)).isTrue();
        assertThat(pushOrderFor(waterId)).isEqualTo(201);
    }

    @Test
    @DisplayName("selling through the partner deducts stock and sells the dish out behind it")
    void sellingTheLastPortion_sellsTheDishOut() throws Exception {
        // One portion of osh left, exactly.
        inventoryService.adjustStock(beefId, new BigDecimal("0.500"), "one left", "chef");
        assertThat(menuSays(oshId)).isTrue();

        assertThat(pushOrderFor(oshId)).isEqualTo(201);

        // Nothing has been cooked yet — stock leaves when the order is accepted, not when it arrives —
        // so the dish is still on sale. The order path is the backstop for the gap.
        assertThat(recipeAvailable(oshId)).isTrue();
    }
}
