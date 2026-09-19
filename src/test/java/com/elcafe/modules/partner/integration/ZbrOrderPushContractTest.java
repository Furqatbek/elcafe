package com.elcafe.modules.partner.integration;

import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import com.elcafe.modules.partner.service.PartnerAccessService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The exact body ZBR should POST to us, proven rather than described.
 *
 * <p>They have built their order push and asked which body we parse, calling it the only thing
 * blocking an end-to-end test. Prose answering that would be a guess about our own code; this is the
 * answer compiled and run, so the schema we send them cannot drift from the schema we accept.
 *
 * <p>Their proposed payload differs from ours in two ways that are not naming, and both are pinned
 * below: {@code paymentMode} is required and their draft omits it, and a product sold by size needs
 * its {@code variantId} or we refuse the order rather than charge a small and cook a large.
 *
 * <p>Everything else is a rename, and their extra fields — item names, unit prices, line totals, the
 * time they took the order — are ignored rather than rejected. That is worth a test of its own: it
 * means they can keep sending their own richer payload and only rename the parts we read.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:zbrcontractit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        "app.partner.auto-accept=false",
        "app.partner.outbox.poll-ms=3600000",
})
class ZbrOrderPushContractTest {

    private static final String KEY_HEADER = "X-Partner-Key";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private PartnerRestaurantRepository partnerRestaurantRepository;
    @Autowired private PartnerAccessService partnerAccessService;
    @Autowired private OrderRepository orderRepository;

    private Long restaurantId;
    private Long plovId;
    private Long largeVariantId;
    private Long teaId;
    private String apiKey;

    private final AtomicLong reference = new AtomicLong();

    @BeforeAll
    void seed() {
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Contract Cafe").address("15 Mustaqillik")
                .active(true).acceptingOrders(true)
                .deliveryFee(new BigDecimal("15000")).build());
        restaurantId = restaurant.getId();

        Category category = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Main").sortOrder(0).active(true).build());

        Product plov = productRepository.save(Product.builder()
                .category(category).name("Plov").price(new BigDecimal("28000"))
                .status(ProductStatus.LIVE).inStock(true).hasVariants(true).build());
        plovId = plov.getId();
        largeVariantId = productVariantRepository.save(ProductVariant.builder()
                .product(plov).name("Large").price(new BigDecimal("30000"))
                .inStock(true).isAvailable(true).sortOrder(0).build()).getId();

        teaId = productRepository.save(Product.builder()
                .category(category).name("Green tea").price(new BigDecimal("8000"))
                .status(ProductStatus.LIVE).inStock(true).build()).getId();

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

    private String nextReference() {
        return String.format("FD-20260919-A7K%03d", reference.incrementAndGet());
    }

    private org.springframework.test.web.servlet.ResultActions push(String body) throws Exception {
        return mvc.perform(post("/api/v1/partner/orders")
                .header(KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("their payload, renamed to our fields, is accepted exactly as they would send it")
    void theirPayloadRenamed_isAccepted() throws Exception {
        String externalOrderId = nextReference();
        // Their draft with our names, and their own extra fields left in untouched: name, unitPrice,
        // lineTotal and placedAt are theirs to send and ours to ignore.
        String body = """
                {
                  "restaurantId": %d,
                  "externalOrderId": "%s",
                  "orderType": "DELIVERY",
                  "paymentMode": "PREPAID",
                  "customer": { "name": "Anvar", "phone": "998901234567" },
                  "delivery": { "address": "Mustaqillik 15, kv 42" },
                  "items": [
                    { "productId": %d, "variantId": %d, "quantity": 2,
                      "specialInstructions": "no onions",
                      "name": "Plov", "unitPrice": 30000, "lineTotal": 60000 }
                  ],
                  "expectedTotal": 75000,
                  "placedAt": "2026-09-19T10:02:11Z"
                }
                """.formatted(restaurantId, externalOrderId, plovId, largeVariantId);

        String response = push(body).andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(response).path("data").path("orderId").asLong();
        // 2 × the Large variant at 30 000, plus the venue's 15 000 delivery fee.
        assertThat(orderRepository.findById(orderId).orElseThrow().getTotal())
                .isEqualByComparingTo("75000");
    }

    @Test
    @DisplayName("fields we do not read are ignored, not rejected")
    void unknownFields_areIgnored() throws Exception {
        // They send a richer order than we consume, and asking them to strip it would be asking them
        // to build a payload for us alone. Anything we do not read simply goes nowhere.
        String body = """
                {
                  "restaurantId": %d, "externalOrderId": "%s",
                  "orderType": "TAKEAWAY", "paymentMode": "CASH",
                  "items": [ { "productId": %d, "quantity": 1 } ],
                  "venueId": "55", "subtotal": 8000, "deliveryFee": 0,
                  "courierName": "Someone", "promoCode": "SUMMER", "placedAt": "2026-09-19T10:02:11Z"
                }
                """.formatted(restaurantId, nextReference(), teaId);

        push(body).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
    }

    @Test
    @DisplayName("ids sent as strings are accepted, because that is how their draft sends them")
    void stringIds_areCoerced() throws Exception {
        // Their example quotes both venueId and productId. Renaming a field is one line on their
        // side; changing its type is a different conversation, so it is worth knowing we do not need
        // to have it. Jackson coerces a numeric string, but assuming that at 2am is how an
        // integration fails on its first real order.
        String body = """
                {
                  "restaurantId": "%d", "externalOrderId": "%s",
                  "orderType": "TAKEAWAY", "paymentMode": "PREPAID",
                  "items": [ { "productId": "%d", "quantity": 1 } ]
                }
                """.formatted(restaurantId, nextReference(), teaId);

        push(body).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
    }

    @Test
    @DisplayName("paymentMode is required — their draft omits it, and we would refuse the order")
    void missingPaymentMode_isRefused() throws Exception {
        // Not pedantry: it decides whether the venue is handed food that is already paid for or
        // money still to collect. Guessing it would eventually cost somebody a meal.
        String body = """
                {
                  "restaurantId": %d, "externalOrderId": "%s", "orderType": "TAKEAWAY",
                  "items": [ { "productId": %d, "quantity": 1 } ]
                }
                """.formatted(restaurantId, nextReference(), teaId);

        push(body).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @DisplayName("a dish sold by size needs its variantId, or the order is refused")
    void missingVariantId_isRefused() throws Exception {
        // Falling back to the base price is how a venue gets underpaid: a Large charged at Regular,
        // cooked Large, with nothing on the ticket to show it. Their expectedTotal would not catch
        // it either, because they quoted from the same base price.
        String body = """
                {
                  "restaurantId": %d, "externalOrderId": "%s",
                  "orderType": "TAKEAWAY", "paymentMode": "PREPAID",
                  "items": [ { "productId": %d, "quantity": 1 } ]
                }
                """.formatted(restaurantId, nextReference(), plovId);

        String response = push(body).andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).path("errors").path("reason").asText())
                .isEqualTo("VARIANT_REQUIRED");
    }

    @Test
    @DisplayName("a replay of the same reference returns the original order, not a second one")
    void replay_returnsTheOriginal() throws Exception {
        String externalOrderId = nextReference();
        String body = """
                {
                  "restaurantId": %d, "externalOrderId": "%s",
                  "orderType": "TAKEAWAY", "paymentMode": "PREPAID",
                  "items": [ { "productId": %d, "quantity": 1 } ]
                }
                """.formatted(restaurantId, externalOrderId, teaId);

        String first = push(body).andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Their retry after a timeout must never produce a second ticket — a double print is a
        // double-cooked order. 200 with duplicate:true is how they tell the two apart.
        String second = push(body).andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).path("data").path("duplicate").asBoolean()).isTrue();
        assertThat(objectMapper.readTree(second).path("data").path("orderId").asLong())
                .isEqualTo(objectMapper.readTree(first).path("data").path("orderId").asLong());
    }

    @Test
    @DisplayName("an expectedTotal that forgot the delivery fee says so, instead of blaming prices")
    void expectedTotalMissingDeliveryFee_isDiagnosed() throws Exception {
        // Exactly the mistake ZBR made reading our contract: they defined their expectedTotal as the
        // sum of line totals and left the delivery fee out. Without the hint the symptom is every
        // delivery order refused for "a price mismatch" while the prices are in fact identical —
        // which sends both sides hunting a stale menu that does not exist.
        String body = """
                {
                  "restaurantId": %d, "externalOrderId": "%s",
                  "orderType": "DELIVERY", "paymentMode": "PREPAID",
                  "customer": { "name": "Anvar", "phone": "998901234567" },
                  "delivery": { "address": "Mustaqillik 15, kv 42" },
                  "items": [ { "productId": %d, "variantId": %d, "quantity": 2 } ],
                  "expectedTotal": 60000
                }
                """.formatted(restaurantId, nextReference(), plovId, largeVariantId);

        String response = push(body).andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isConflict())
                .andReturn().getResponse().getContentAsString();

        var errors = objectMapper.readTree(response).path("errors");
        assertThat(errors.path("hint").asText()).contains("deliveryFee");
        // The numbers that make it self-diagnosing: theirs equals our goods exactly.
        assertThat(errors.path("subtotal").asInt()).isEqualTo(60000);
        assertThat(errors.path("actualTotal").asInt()).isEqualTo(75000);
    }

    @Test
    @DisplayName("a genuine price disagreement is not mislabelled as a missing delivery fee")
    void genuinePriceDrift_getsNoMisleadingHint() throws Exception {
        String body = """
                {
                  "restaurantId": %d, "externalOrderId": "%s",
                  "orderType": "TAKEAWAY", "paymentMode": "PREPAID",
                  "items": [ { "productId": %d, "quantity": 1 } ],
                  "expectedTotal": 5000
                }
                """.formatted(restaurantId, nextReference(), teaId);

        String response = push(body).andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isConflict())
                .andReturn().getResponse().getContentAsString();

        // Tea is 8000 and there is no delivery fee on a takeaway: this really is a stale price, and
        // a hint pointing at the delivery fee would send them looking in the wrong place.
        assertThat(objectMapper.readTree(response).path("errors").path("hint").isMissingNode()).isTrue();
    }

    @Test
    @DisplayName("expectedTotal is checked against channel prices, so a stale quote is caught")
    void wrongExpectedTotal_isRefused() throws Exception {
        String body = """
                {
                  "restaurantId": %d, "externalOrderId": "%s",
                  "orderType": "TAKEAWAY", "paymentMode": "PREPAID",
                  "items": [ { "productId": %d, "quantity": 1 } ],
                  "expectedTotal": 1
                }
                """.formatted(restaurantId, nextReference(), teaId);

        // Sending it is optional; sending a wrong one is a conflict rather than a silent overcharge.
        push(body).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
    }
}
