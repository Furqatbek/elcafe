package com.elcafe.modules.partner.integration;

import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The restaurant accepting on the aggregator's own tablet has to move the order here.
 *
 * <p>Without this the two systems disagree about the same order: their app shows it accepted and
 * being cooked, our kitchen screen still shows it waiting, and eventually two people decide it
 * separately. This is the inbound half of "accept or decline from either system" — the outbound half
 * already existed.
 *
 * <p>The properties worth pinning are the ones a webhook lives or dies by. Delivery is at-least-once
 * on anyone's integration, so the same ACCEPTED will arrive twice and must not error the second time.
 * A state we cannot reach has to be refused rather than quietly applied. And a change they told us
 * about must not be sent straight back to them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(PartnerStatusCallbackIntegrationTest.TestDispatcherConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:partnerstatusit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        // Staff accept manually here, so an inbound ACCEPTED has somewhere to move the order from.
        "app.partner.auto-accept=false",
        "app.partner.outbox.poll-ms=3600000",
})
class PartnerStatusCallbackIntegrationTest {

    private static final String KEY_HEADER = "X-Partner-Key";

    /** Claims the partner so events are queued — the publisher drops events nobody can deliver. */
    static class ClaimingDispatcher implements PartnerEventDispatcher {
        @Override
        public boolean supports(Partner partner) {
            return "zbr".equals(partner.getSlug());
        }

        @Override
        public void dispatch(Partner partner, IntegrationEvent event) {
            // Delivery is the outbox test's subject, not this one's.
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
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private PartnerRestaurantRepository partnerRestaurantRepository;
    @Autowired private IntegrationEventRepository integrationEventRepository;
    @Autowired private PartnerAccessService partnerAccessService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private com.elcafe.modules.order.service.OrderService orderService;

    private Long restaurantId;
    private Long productId;
    private String apiKey;

    private final AtomicLong externalOrderCounter = new AtomicLong();

    @BeforeAll
    void seed() {
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Callback Cafe").address("11 Test St")
                .active(true).acceptingOrders(true).build());
        restaurantId = restaurant.getId();

        Category category = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Main").sortOrder(0).active(true).build());
        productId = productRepository.save(Product.builder()
                .category(category).name("Osh").price(new BigDecimal("30000"))
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

    @BeforeEach
    void clearEvents() {
        integrationEventRepository.deleteAll();
    }

    private record PushedOrder(String externalId, Long orderId) {}

    private PushedOrder pushOrder() throws Exception {
        String externalId = "CB-" + externalOrderCounter.incrementAndGet();
        String body = mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":" + restaurantId + ",\"externalOrderId\":\"" + externalId
                                + "\",\"orderType\":\"TAKEAWAY\",\"paymentMode\":\"PREPAID\","
                                + "\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new PushedOrder(externalId, objectMapper.readTree(body).path("data").path("orderId").asLong());
    }

    private int reportStatus(String externalId, String status) throws Exception {
        return reportStatus(externalId, status, null);
    }

    private int reportStatus(String externalId, String status, String reason) throws Exception {
        String body = reason == null
                ? "{\"status\":\"" + status + "\"}"
                : "{\"status\":\"" + status + "\",\"reason\":\"" + reason + "\"}";
        return mvc.perform(post("/api/v1/partner/orders/" + externalId + "/status")
                        .header(KEY_HEADER, apiKey)
                        .param("restaurantId", String.valueOf(restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private OrderStatus statusOf(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("accepting in the partner's app accepts the order here")
    void acceptFromPartnerApp_movesTheOrder() throws Exception {
        PushedOrder order = pushOrder();

        assertThat(reportStatus(order.externalId(), "ACCEPTED")).isEqualTo(200);

        assertThat(statusOf(order.orderId())).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("the same status arriving twice is fine — a webhook is delivered at least once")
    void repeatedStatus_isIdempotent() throws Exception {
        PushedOrder order = pushOrder();

        assertThat(reportStatus(order.externalId(), "ACCEPTED")).isEqualTo(200);
        // Our own rules refuse ACCEPTED -> ACCEPTED. A retry must not be punished for that.
        assertThat(reportStatus(order.externalId(), "ACCEPTED")).isEqualTo(200);

        assertThat(statusOf(order.orderId())).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("a state we cannot reach from here is refused, not quietly applied")
    void impossibleTransition_isRefused() throws Exception {
        PushedOrder order = pushOrder();
        OrderStatus before = statusOf(order.orderId());

        // Straight to delivered, skipping accept and the kitchen entirely.
        assertThat(reportStatus(order.externalId(), "DELIVERED")).isEqualTo(409);

        assertThat(statusOf(order.orderId())).isEqualTo(before);
    }

    @Test
    @DisplayName("their states that mean nothing here are acknowledged rather than refused")
    void statesWithNoLocalMeaning_areAcknowledged() throws Exception {
        PushedOrder order = pushOrder();
        OrderStatus before = statusOf(order.orderId());

        // CREATED tells us an order we created exists. REFUNDED is a fact about money, reachable on
        // their side from DELIVERED — forcing it onto an order state would cancel a delivered order.
        assertThat(reportStatus(order.externalId(), "CREATED")).isEqualTo(200);
        assertThat(reportStatus(order.externalId(), "REFUNDED")).isEqualTo(200);

        assertThat(statusOf(order.orderId())).isEqualTo(before);
    }

    @Test
    @DisplayName("declining in their app cancels the order here, with the reason kept")
    void declineFromPartnerApp_cancelsTheOrder() throws Exception {
        PushedOrder order = pushOrder();

        assertThat(reportStatus(order.externalId(), "CANCELLED", "Kitchen closing early")).isEqualTo(200);

        assertThat(statusOf(order.orderId())).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("a change they told us about is not sent straight back to them")
    void partnerOriginatedChange_isNotEchoed() throws Exception {
        PushedOrder order = pushOrder();
        integrationEventRepository.deleteAll();

        reportStatus(order.externalId(), "ACCEPTED");

        List<IntegrationEvent> events = integrationEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == IntegrationEventType.ORDER_STATUS_CHANGED)
                .toList();
        assertThat(events)
                .as("they already know: informing each other of a shared fact is pure traffic")
                .isEmpty();
    }

    @Test
    @DisplayName("cancelling is free right up to the moment the kitchen starts")
    void cancelBeforePreparing_isAccepted() throws Exception {
        PushedOrder order = pushOrder();
        reportStatus(order.externalId(), "ACCEPTED");

        // Accepted but not yet cooking: nothing has been spent, so the customer changes their mind
        // for free.
        assertThat(reportStatus(order.externalId(), "CANCELLED", "Customer changed their mind"))
                .isEqualTo(200);

        assertThat(statusOf(order.orderId())).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("once the kitchen has started, a customer cancellation is refused")
    void cancelAfterPreparing_isRefused() throws Exception {
        PushedOrder order = pushOrder();
        reportStatus(order.externalId(), "ACCEPTED");
        reportStatus(order.externalId(), "PREPARING");

        // 422, not 409: an order only moves further forward, so this can never succeed and must not
        // be retried. The venue has bought the ingredients and spent the time; absorbing that
        // silently is what the cutoff exists to stop.
        assertThat(reportStatus(order.externalId(), "CANCELLED", "Customer changed their mind"))
                .isEqualTo(422);

        assertThat(statusOf(order.orderId())).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    @DisplayName("food already cooked and waiting is past the line too")
    void cancelAfterReady_isRefused() throws Exception {
        PushedOrder order = pushOrder();
        reportStatus(order.externalId(), "ACCEPTED");
        reportStatus(order.externalId(), "PREPARING");
        reportStatus(order.externalId(), "READY");

        assertThat(reportStatus(order.externalId(), "CANCELLED")).isEqualTo(422);

        assertThat(statusOf(order.orderId())).isEqualTo(OrderStatus.READY);
    }

    @Test
    @DisplayName("the refusal names the cutoff, so their client can say why to a customer")
    void refusalCarriesTheReason() throws Exception {
        PushedOrder order = pushOrder();
        reportStatus(order.externalId(), "ACCEPTED");
        reportStatus(order.externalId(), "PREPARING");

        String body = mvc.perform(post("/api/v1/partner/orders/" + order.externalId() + "/status")
                        .header(KEY_HEADER, apiKey)
                        .param("restaurantId", String.valueOf(restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andReturn().getResponse().getContentAsString();

        var details = objectMapper.readTree(body).path("errors");
        assertThat(details.path("reason").asText()).isEqualTo("CANCELLATION_WINDOW_CLOSED");
        assertThat(details.path("currentStatus").asText()).isEqualTo("PREPARING");
        assertThat(details.path("cancellableUntil").asText()).isEqualTo("PREPARING");
    }

    @Test
    @DisplayName("staff keep every option: the cutoff binds the customer, not the kitchen")
    void staffCancellationIsUnaffected() throws Exception {
        PushedOrder order = pushOrder();
        reportStatus(order.externalId(), "ACCEPTED");
        reportStatus(order.externalId(), "PREPARING");

        // A fire, a spoiled delivery, a customer at the counter — exactly the case where the
        // manager's judgement should win over a rule protecting their revenue.
        orderService.cancelOrder(order.orderId(), "Kitchen incident", "manager");

        assertThat(statusOf(order.orderId())).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("an order id that is not theirs is not found, whoever it belongs to")
    void unknownExternalId_is404() throws Exception {
        assertThat(reportStatus("never-pushed-this", "ACCEPTED")).isEqualTo(404);
    }
}
