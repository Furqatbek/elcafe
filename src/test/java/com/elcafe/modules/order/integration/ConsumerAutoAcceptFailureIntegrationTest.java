package com.elcafe.modules.order.integration;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The consumer-path twin of {@code PartnerAutoAcceptFailureIntegrationTest}, pinning the same class of
 * bug on the channel that carries our own customers' money.
 *
 * <p>{@code ConsumerOrderService.placeOrder} used to call {@link com.elcafe.modules.order.service.OrderService#updateOrderStatus}
 * for the Telegram auto-accept from inside its own transaction. That method is
 * {@code @Transactional(REQUIRED)}, so it joined; accepting checks ingredient availability and
 * legitimately throws when the kitchen is short; Spring marked the shared transaction rollback-only;
 * and the catch block — written precisely to make the accept best-effort — could not undo that. The
 * commit failed with {@code UnexpectedRollbackException}, the customer got a 500, and the order
 * vanished <em>after</em> its wallet payment had been debited in that same transaction and its kitchen
 * ticket had printed.
 *
 * <p>Mockito cannot see this: a mocked {@code OrderService} carries no transaction interceptor, so a
 * unit test of the same code asserts the opposite and passes. Only a real context with a real
 * transaction manager shows it, which is what this test is for.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:consumeracceptfailit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        // Auto-accept ON: the failing path is the default one.
        "app.telegram.miniapp.auto-accept=true",
        "logging.level.com.elcafe.security.JwtAuthenticationFilter=OFF",
})
class ConsumerAutoAcceptFailureIntegrationTest {

    private static final String PHONE = "+998901239876";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    /** Forced to refuse the accept, standing in for a kitchen that is short an ingredient. */
    @MockBean private InventoryService inventoryService;

    private Long restaurantId;
    private Long productId;
    private Long customerId;

    @BeforeAll
    void seed() {
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Consumer Accept Fail Cafe").address("5 Test St")
                .active(true).acceptingOrders(true).build());
        restaurantId = restaurant.getId();

        Category category = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Main").sortOrder(0).active(true).build());
        productId = productRepository.save(Product.builder()
                .category(category).name("Osh").price(new BigDecimal("30000"))
                .status(ProductStatus.LIVE).inStock(true).build()).getId();

        customerId = customerRepository.save(Customer.builder()
                .restaurantId(restaurantId).phone(PHONE)
                .firstName("Ali").lastName("Valiyev").active(true).build()).getId();
    }

    /** Mints the same consumer token ConsumerAuthService issues, without the OTP round trip. */
    private String consumerToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(PHONE)
                .claim("customerId", customerId)
                .claim("restaurantId", restaurantId)
                .claim("type", "consumer")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("a failing Telegram auto-accept must not destroy the customer's order")
    void autoAcceptFailure_leavesOrderStanding() throws Exception {
        when(inventoryService.checkIngredientAvailability(any())).thenReturn(false);

        String body = mvc.perform(post("/api/v1/consumer/orders")
                        .header("Authorization", "Bearer " + consumerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":" + restaurantId + ",\"orderSource\":\"TELEGRAM_BOT\","
                                + "\"orderType\":\"TAKEAWAY\",\"paymentMethod\":\"CASH\","
                                + "\"customerInfo\":{\"firstName\":\"Ali\",\"phone\":\"" + PHONE + "\"},"
                                + "\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        // It waits at NEW for a human, which is what the catch block always intended.
        assertThat(data.path("status").asText()).isEqualTo("NEW");

        Order persisted = orderRepository.findByOrderNumber(data.path("orderNumber").asText()).orElse(null);
        assertThat(persisted)
                .as("the order must survive a refused accept — the customer has ordered and paid")
                .isNotNull();
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.NEW);
    }
}
