package com.elcafe.modules.partner.integration;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import com.elcafe.modules.partner.service.PartnerAccessService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.settings.entity.PrinterSettings;
import com.elcafe.modules.settings.repository.PrinterSettingsRepository;
import com.elcafe.modules.settings.repository.PrintJobRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the promise the whole integration was built on: an order that arrives from outside produces a
 * kitchen ticket. Everything else — statuses, totals, correlation rows — is bookkeeping the venue never
 * sees; the paper is what they actually work from.
 *
 * <p>Until this existed, nothing anywhere asserted that a {@code print_jobs} row is created. Every other
 * test would have passed just as happily against a build that never printed anything, which is exactly
 * the failure mode this feature exists to prevent, and exactly the one that shipped once already.
 *
 * <p>Auto-accept is deliberately <b>off</b> on both channels here. That is the configuration where
 * printing is easiest to lose: the order sits at NEW, and accepting it by hand only ever creates the KDS
 * ticket — never a print job. A venue that turns auto-accept on to review orders first must still get
 * paper, so the switch decides whether the order jumps to the kitchen display, never whether it prints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:printonarrivalit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        // The print-agent queue is what puts a row in print_jobs; it is the production default.
        "app.printing.use-agent=true",
        // Both switches OFF: printing must not depend on either.
        "app.partner.auto-accept=false",
        "app.telegram.miniapp.auto-accept=false",
        "logging.level.com.elcafe.security.JwtAuthenticationFilter=OFF",
})
class PrintOnArrivalIntegrationTest {

    private static final String KEY_HEADER = "X-Partner-Key";
    private static final String PHONE = "+998901234567";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PrinterSettingsRepository printerSettingsRepository;
    @Autowired private PrintJobRepository printJobRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private PartnerRestaurantRepository partnerRestaurantRepository;
    @Autowired private PartnerAccessService partnerAccessService;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private Long restaurantId;
    private Long productId;
    private Long customerId;
    private String apiKey;

    @BeforeAll
    void seed() {
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Printing Cafe").address("6 Test St")
                .active(true).acceptingOrders(true).build());
        restaurantId = restaurant.getId();

        // No kitchen stations, so PrintService takes its legacy single-printer path — which needs an
        // enabled KITCHEN printer to exist at all. Without one it logs and silently returns, which is
        // itself worth knowing: print-on-arrival is real, but only for a venue that configured a printer.
        printerSettingsRepository.save(PrinterSettings.builder()
                .restaurant(restaurant)
                .printerType(PrinterSettings.PrinterType.KITCHEN)
                .printerName("Kitchen-1")
                .connectionType("NETWORK")
                .ipAddress("127.0.0.1")
                .port(9100)
                .enabled(true)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Main").sortOrder(0).active(true).build());
        productId = productRepository.save(Product.builder()
                .category(category).name("Osh").price(new BigDecimal("30000"))
                .status(ProductStatus.LIVE).inStock(true).build()).getId();

        customerId = customerRepository.save(Customer.builder()
                .restaurantId(restaurantId).phone(PHONE)
                .firstName("Ali").lastName("Valiyev").active(true).build()).getId();

        apiKey = partnerAccessService.generateApiKey();
        Partner partner = partnerRepository.save(Partner.builder()
                .name("Printing Aggregator").slug("printing-agg")
                .apiKeyHash(partnerAccessService.hashApiKey(apiKey))
                .apiKeyPrefix(partnerAccessService.prefixOf(apiKey))
                .active(true).build());
        partnerRestaurantRepository.save(PartnerRestaurant.builder()
                .partnerId(partner.getId()).restaurantId(restaurantId)
                .canReadMenu(true).canPushOrders(true).active(true).build());
    }

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
    @DisplayName("an aggregator order prints on arrival, with auto-accept OFF")
    void partnerOrder_printsOnArrival() throws Exception {
        long before = printJobRepository.count();

        String body = mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":" + restaurantId + ",\"externalOrderId\":\"EXT-PRINT-1\","
                                + "\"orderType\":\"TAKEAWAY\",\"paymentMode\":\"PREPAID\","
                                + "\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        // Auto-accept is off, so it waits for a human — and must still have printed.
        assertThat(data.path("status").asText()).isEqualTo("NEW");

        assertThat(printJobRepository.count())
                .as("a kitchen ticket must be queued for the venue's print agent")
                .isGreaterThan(before);
    }

    @Test
    @DisplayName("a Telegram Mini App order prints on arrival, with auto-accept OFF")
    void telegramOrder_printsOnArrival() throws Exception {
        long before = printJobRepository.count();

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
        assertThat(data.path("status").asText()).isEqualTo("NEW");

        // The regression: gating the print on the auto-accept switch meant a venue that reviewed its
        // Telegram orders got one that never printed at any point in its life, because accepting by
        // hand only creates the KDS ticket.
        assertThat(printJobRepository.count())
                .as("a Telegram order must print even when it is not auto-accepted")
                .isGreaterThan(before);
    }

    @Test
    @DisplayName("a website order does NOT print on arrival — it waits for a human to accept it")
    void websiteOrder_doesNotPrintOnArrival() throws Exception {
        long before = printJobRepository.count();

        mvc.perform(post("/api/v1/consumer/orders")
                        .header("Authorization", "Bearer " + consumerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":" + restaurantId + ",\"orderSource\":\"WEBSITE\","
                                + "\"orderType\":\"TAKEAWAY\",\"paymentMethod\":\"CASH\","
                                + "\"customerInfo\":{\"firstName\":\"Ali\",\"phone\":\"" + PHONE + "\"},"
                                + "\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated());

        // Deliberate asymmetry, pinned so nobody "fixes" it into printing every web order on arrival:
        // a website order is an unreviewed request, not a committed sale like an aggregator's.
        assertThat(printJobRepository.count())
                .as("website orders must not print until staff accept them")
                .isEqualTo(before);
    }
}
