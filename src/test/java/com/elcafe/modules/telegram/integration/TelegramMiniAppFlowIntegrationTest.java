package com.elcafe.modules.telegram.integration;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.repository.KitchenOrderRepository;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.telegram.entity.TelegramBotConfig;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.repository.TelegramBotConfigRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The end-to-end smoke test a real Telegram client can't be run for in this environment: it boots the
 * FULL application context and drives the Telegram Mini App ordering flow over real HTTP through the
 * ACTUAL production security filter chain — signing {@code initData} exactly as Telegram does, so the
 * login is verified by the real {@link com.elcafe.modules.telegram.service.TelegramInitDataValidator},
 * and the consumer JWT it mints is what authorizes the subsequent order.
 *
 * <p>It proves the seams the unit tests mock away: the signed launch authenticates ({@code
 * /consumer/auth/telegram}), the minted consumer token clears the security chain on {@code
 * /consumer/orders}, a TELEGRAM_BOT order auto-accepts and lands a ticket on the KDS, a tampered
 * payload is rejected, and a wallet-paid order debits the balance and settles the payment.
 *
 * <p>What it deliberately does NOT cover (no infrastructure for it here): Telegram's own WebApp
 * rendering, and Payme/Click's actual hosted checkout + webhooks — only our side of those contracts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tgminiappflowit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        // Auto-accept is the behaviour under test (Telegram order reaches the KDS immediately).
        "app.telegram.miniapp.auto-accept=true",
        "logging.level.com.elcafe.security.JwtAuthenticationFilter=OFF",
})
class TelegramMiniAppFlowIntegrationTest {

    private static final String AUTH = "Authorization";
    private static final String BOT_TOKEN = "7654321:AA_integration_test_bot_token_not_real";
    private static final long TG_USER_ID = 424242L;
    private static final String PHONE = "+998901112233";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private TelegramBotConfigRepository telegramBotConfigRepository;
    @Autowired private TelegramSubscriberRepository telegramSubscriberRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerLoyaltyRepository customerLoyaltyRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private KitchenOrderRepository kitchenOrderRepository;

    private Long restaurantId;
    private Long productId;
    private final BigDecimal price = new BigDecimal("30000");

    // Established by the shared login in seed(), reused across the ordering tests.
    private String accessToken;
    private Long customerId;

    @BeforeAll
    void seed() throws Exception {
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("TG Cafe").address("1 Test St").active(true).acceptingOrders(true).build());
        restaurantId = restaurant.getId();

        telegramBotConfigRepository.save(TelegramBotConfig.builder()
                .restaurantId(restaurantId).botToken(BOT_TOKEN).botUsername("tg_flow_bot").isActive(true).build());

        // Subscriber who completed the bot wizard (has a verified phone) — the login requirement.
        telegramSubscriberRepository.save(TelegramSubscriber.builder()
                .restaurantId(restaurantId).telegramUserId(TG_USER_ID).phone(PHONE)
                .isActive(true).isBlocked(false).subscribedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());

        Category category = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Main").sortOrder(0).active(true).build());
        productId = productRepository.save(Product.builder()
                .category(category).name("Osh").price(price).status(ProductStatus.LIVE).inStock(true).build()).getId();

        // Log in exactly as the Mini App does: signed initData → consumer session.
        String initData = signInitData(BOT_TOKEN, TG_USER_ID, Instant.now().getEpochSecond());
        String body = mvc.perform(post("/api/v1/consumer/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(restaurantId, initData)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.path("registration_required").asBoolean()).isFalse();
        assertThat(data.path("prefill").path("phone").asText()).isEqualTo(PHONE);
        accessToken = data.path("auth").path("access_token").asText();
        customerId = data.path("auth").path("customer_id").asLong();
        assertThat(accessToken).isNotBlank();
        assertThat(customerId).isPositive();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("signed launch → consumer token places a TELEGRAM_BOT order that auto-accepts onto the KDS")
    void telegramOrder_reachesKds() throws Exception {
        String orderNumber = placeOrder("CASH");

        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(order.getOrderSource().name()).isEqualTo("TELEGRAM_BOT");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED); // auto-accepted

        Optional<KitchenOrder> ticket = kitchenOrderRepository.findByOrderId(order.getId());
        assertThat(ticket).as("a kitchen ticket must exist — the order reached the KDS").isPresent();
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("a tampered initData is rejected by the real chain (401), so no forged login is possible")
    void tamperedInitData_isRejected() throws Exception {
        // Valid signature for one user, then swap the user id it vouches for.
        long authDate = Instant.now().getEpochSecond();
        String goodHash = computeHash(BOT_TOKEN, userJson(TG_USER_ID), authDate);
        String forged = "auth_date=" + authDate
                + "&user=" + enc(userJson(999999L))     // different user, original hash
                + "&hash=" + goodHash;

        mvc.perform(post("/api/v1/consumer/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(restaurantId, forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("wallet payment debits the balance and settles the payment as COMPLETED")
    void walletPayment_debitsAndSettles() throws Exception {
        // Fund the wallet above the order total.
        Customer customer = customerRepository.findById(customerId).orElseThrow();
        CustomerLoyalty loyalty = customerLoyaltyRepository.findByCustomerId(customerId)
                .orElseGet(() -> CustomerLoyalty.builder().customer(customer).restaurantId(restaurantId).build());
        loyalty.setCurrentBalance(new BigDecimal("100000"));
        customerLoyaltyRepository.save(loyalty);

        String orderNumber = placeOrder("WALLET");

        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        Payment payment = paymentRepository.findByOrderId(order.getId()).get(0);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        BigDecimal after = customerLoyaltyRepository.findByCustomerId(customerId).orElseThrow().getCurrentBalance();
        assertThat(after).isEqualByComparingTo(new BigDecimal("70000")); // 100000 - 30000
    }

    // --- helpers ---

    /** Place a one-item takeaway order for the seeded product; returns the created order number. */
    private String placeOrder(String paymentMethod) throws Exception {
        String json = "{"
                + "\"restaurantId\":" + restaurantId + ","
                + "\"orderSource\":\"TELEGRAM_BOT\","
                + "\"orderType\":\"TAKEAWAY\","
                + "\"customerInfo\":{\"phone\":\"" + PHONE + "\"},"
                + "\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                + "\"paymentMethod\":\"" + paymentMethod + "\""
                + "}";
        String body = mvc.perform(post("/api/v1/consumer/orders")
                        .header(AUTH, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("orderNumber").asText();
    }

    private static String loginJson(Long restaurantId, String initData) {
        return "{\"restaurant_id\":" + restaurantId + ",\"init_data\":\"" + initData.replace("\"", "\\\"") + "\"}";
    }

    private static String userJson(long id) {
        return "{\"id\":" + id + ",\"first_name\":\"Ali\",\"last_name\":\"Valiyev\",\"language_code\":\"uz\"}";
    }

    /** Build signed initData the way a Telegram client does (fields url-encoded, hash appended). */
    private static String signInitData(String token, long userId, long authDate) {
        String user = userJson(userId);
        String hash = computeHash(token, user, authDate);
        return "auth_date=" + authDate + "&user=" + enc(user) + "&hash=" + hash;
    }

    private static String computeHash(String token, String user, long authDate) {
        TreeMap<String, String> fields = new TreeMap<>();
        fields.put("auth_date", String.valueOf(authDate));
        fields.put("user", user);
        StringBuilder dcs = new StringBuilder();
        boolean first = true;
        for (var e : fields.entrySet()) {
            if (!first) dcs.append('\n');
            dcs.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        return toHex(hmac(secret, dcs.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
