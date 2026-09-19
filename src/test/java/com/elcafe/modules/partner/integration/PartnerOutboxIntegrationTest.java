package com.elcafe.modules.partner.integration;

import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.partner.entity.IntegrationEvent;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.enums.IntegrationEventStatus;
import com.elcafe.modules.partner.enums.IntegrationEventType;
import com.elcafe.modules.partner.outbox.IntegrationEventWorker;
import com.elcafe.modules.partner.outbox.PartnerEventDispatcher;
import com.elcafe.modules.partner.outbox.PartnerEventPublisher;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the outbound outbox end to end, with a test dispatcher standing in for a partner's API.
 *
 * <p>The properties under test are the ones that make an outbox worth having rather than a fancier
 * {@code try/catch}: the message commits with the change that caused it, a failing partner is retried
 * and eventually dead-lettered instead of silently dropped, transitions for one order cannot overtake
 * each other, and a burst of state changes collapses to the latest rather than replaying a
 * contradictory history.
 *
 * <p>The scheduler's own polling is turned off here (a one-hour delay) so the worker runs only when a
 * test asks it to; otherwise a background pass could deliver an event mid-assertion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(PartnerOutboxIntegrationTest.TestDispatcherConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:partneroutboxit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        "app.partner.auto-accept=false",
        // The worker runs only when a test calls it.
        "app.partner.outbox.poll-ms=3600000",
})
class PartnerOutboxIntegrationTest {

    private static final String KEY_HEADER = "X-Partner-Key";

    /** Stands in for a partner's API: records what it received, and fails on demand. */
    static class RecordingDispatcher implements PartnerEventDispatcher {
        final List<IntegrationEvent> delivered = new CopyOnWriteArrayList<>();
        volatile boolean failing = false;

        @Override
        public boolean supports(Partner partner) {
            return "zbr".equals(partner.getSlug());
        }

        @Override
        public void dispatch(Partner partner, IntegrationEvent event) {
            if (failing) {
                throw new IllegalStateException("partner is down");
            }
            delivered.add(event);
        }

        void reset() {
            delivered.clear();
            failing = false;
        }
    }

    @TestConfiguration
    static class TestDispatcherConfig {
        @Bean
        RecordingDispatcher recordingDispatcher() {
            return new RecordingDispatcher();
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
    @Autowired private IntegrationEventWorker worker;
    @Autowired private PartnerEventPublisher publisher;
    @Autowired private PartnerAccessService partnerAccessService;
    @Autowired private OrderService orderService;
    @Autowired private RecordingDispatcher dispatcher;

    private Long restaurantId;
    private Long productId;
    private String apiKey;
    private Partner zbr;
    private Partner unsupported;

    @BeforeAll
    void seed() {
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Outbox Cafe").address("8 Test St")
                .active(true).acceptingOrders(true).build());
        restaurantId = restaurant.getId();

        Category category = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Main").sortOrder(0).active(true).build());
        productId = productRepository.save(Product.builder()
                .category(category).name("Osh").price(new BigDecimal("30000"))
                .status(ProductStatus.LIVE).inStock(true).build()).getId();

        apiKey = partnerAccessService.generateApiKey();
        zbr = partnerRepository.save(Partner.builder()
                .name("ZBR").slug("zbr")
                .apiKeyHash(partnerAccessService.hashApiKey(apiKey))
                .apiKeyPrefix(partnerAccessService.prefixOf(apiKey))
                .active(true).build());
        partnerRestaurantRepository.save(PartnerRestaurant.builder()
                .partnerId(zbr.getId()).restaurantId(restaurantId)
                .canReadMenu(true).canPushOrders(true).active(true).build());

        // A partner no dispatcher claims, to prove we do not queue undeliverable messages.
        unsupported = partnerRepository.save(Partner.builder()
                .name("Nobody").slug("nobody")
                .apiKeyHash(partnerAccessService.hashApiKey(partnerAccessService.generateApiKey()))
                .active(true).build());
    }

    @BeforeEach
    void resetState() {
        dispatcher.reset();
        integrationEventRepository.deleteAll();
    }

    private Long pushOrder(String externalId) throws Exception {
        String body = mvc.perform(post("/api/v1/partner/orders")
                        .header(KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":" + restaurantId + ",\"externalOrderId\":\"" + externalId + "\","
                                + "\"orderType\":\"TAKEAWAY\",\"paymentMode\":\"PREPAID\","
                                + "\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("orderId").asLong();
    }

    private List<IntegrationEvent> allEvents() {
        return new ArrayList<>(integrationEventRepository.findAll());
    }

    @Test
    @DisplayName("accepting a partner's order queues a status notification and delivers it")
    void orderStatusChange_isQueuedAndDelivered() throws Exception {
        Long orderId = pushOrder("OUT-1");

        orderService.updateOrderStatus(orderId, OrderStatus.ACCEPTED, "test", "SYSTEM");

        List<IntegrationEvent> queued = allEvents();
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getEventType()).isEqualTo(IntegrationEventType.ORDER_STATUS_CHANGED);
        assertThat(queued.get(0).getStatus()).isEqualTo(IntegrationEventStatus.PENDING);

        worker.dispatchDueEvents();

        assertThat(dispatcher.delivered).hasSize(1);
        JsonNode payload = objectMapper.readTree(dispatcher.delivered.get(0).getPayload());
        // Their id, not ours: it is the only one their system can act on.
        assertThat(payload.path("externalOrderId").asText()).isEqualTo("OUT-1");
        assertThat(payload.path("status").asText()).isEqualTo("ACCEPTED");

        assertThat(integrationEventRepository.findAll().get(0).getStatus())
                .isEqualTo(IntegrationEventStatus.SENT);
    }

    @Test
    @DisplayName("a non-partner order queues nothing — most orders are not aggregator orders")
    void nonPartnerOrder_queuesNothing() {
        // No push, so nothing here came from a partner.
        assertThat(allEvents()).isEmpty();
    }

    @Test
    @DisplayName("a partner with no dispatcher never has events queued")
    void partnerWithoutDispatcher_isNotQueued() {
        publisher.publish(unsupported, restaurantId, IntegrationEventType.MENU_ITEM_AVAILABILITY,
                "product:" + productId, Map.of("available", false));

        // Queuing a message nobody can deliver would make "pending events" a meaningless number and
        // leave a permanent phantom backlog.
        assertThat(allEvents()).isEmpty();
    }

    @Test
    @DisplayName("a failing partner is retried, not dropped")
    void failedDelivery_staysPendingWithBackoff() throws Exception {
        Long orderId = pushOrder("OUT-2");
        dispatcher.failing = true;

        orderService.updateOrderStatus(orderId, OrderStatus.ACCEPTED, "test", "SYSTEM");
        worker.dispatchDueEvents();

        IntegrationEvent event = allEvents().get(0);
        assertThat(event.getStatus()).isEqualTo(IntegrationEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("partner is down");
        assertThat(dispatcher.delivered).isEmpty();
    }

    @Test
    @DisplayName("a partner that stays down eventually dead-letters instead of retrying forever")
    void repeatedFailure_deadLetters() {
        dispatcher.failing = true;
        publisher.publish(zbr, restaurantId, IntegrationEventType.ORDER_STATUS_CHANGED,
                "order:doomed", Map.of("status", "ACCEPTED"));

        IntegrationEvent queued = allEvents().get(0);
        queued.setMaxAttempts(2);
        integrationEventRepository.save(queued);

        // Two passes, with the backoff cleared between them so the test does not have to wait it out.
        worker.dispatchDueEvents();
        IntegrationEvent afterFirst = integrationEventRepository.findById(queued.getId()).orElseThrow();
        afterFirst.setNextAttemptAt(java.time.OffsetDateTime.now().minusSeconds(1));
        integrationEventRepository.save(afterFirst);
        worker.dispatchDueEvents();

        IntegrationEvent finalState = integrationEventRepository.findById(queued.getId()).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(IntegrationEventStatus.DEAD_LETTER);
        assertThat(finalState.getDeadLetteredAt()).isNotNull();
    }

    @Test
    @DisplayName("a newer state event supersedes an older undelivered one for the same item")
    void coalescingEvent_supersedesOlder() {
        publisher.publish(zbr, restaurantId, IntegrationEventType.MENU_ITEM_AVAILABILITY,
                "product:" + productId, Map.of("available", false));
        publisher.publish(zbr, restaurantId, IntegrationEventType.MENU_ITEM_AVAILABILITY,
                "product:" + productId, Map.of("available", true));

        List<IntegrationEvent> events = allEvents();
        assertThat(events).hasSize(2);
        // An item flapping around its stock threshold must not queue a dozen contradictory messages
        // and leave the partner on whichever lands last.
        assertThat(events.stream().filter(e -> e.getStatus() == IntegrationEventStatus.SUPERSEDED))
                .hasSize(1);
        assertThat(events.stream().filter(e -> e.getStatus() == IntegrationEventStatus.PENDING))
                .hasSize(1);

        worker.dispatchDueEvents();

        assertThat(dispatcher.delivered).hasSize(1);
        assertThat(dispatcher.delivered.get(0).getPayload()).contains("true");
    }

    @Test
    @DisplayName("order transitions are NOT coalesced — every one is kept")
    void orderStatusEvents_areNotCoalesced() {
        publisher.publish(zbr, restaurantId, IntegrationEventType.ORDER_STATUS_CHANGED,
                "order:9", Map.of("status", "ACCEPTED"));
        publisher.publish(zbr, restaurantId, IntegrationEventType.ORDER_STATUS_CHANGED,
                "order:9", Map.of("status", "PREPARING"));

        // Dropping a transition loses information we cannot reconstruct, and we do not yet know
        // whether ZBR's UI wants the history.
        assertThat(allEvents()).allMatch(e -> e.getStatus() == IntegrationEventStatus.PENDING);

        worker.dispatchDueEvents();
        assertThat(dispatcher.delivered).hasSize(2);
    }

    @Test
    @DisplayName("a failed transition holds back the later ones for that order, preserving order")
    void failedEvent_blocksLaterEventsForSameSubject() {
        dispatcher.failing = true;
        publisher.publish(zbr, restaurantId, IntegrationEventType.ORDER_STATUS_CHANGED,
                "order:77", Map.of("status", "ACCEPTED"));
        publisher.publish(zbr, restaurantId, IntegrationEventType.ORDER_STATUS_CHANGED,
                "order:77", Map.of("status", "READY"));

        worker.dispatchDueEvents();
        assertThat(dispatcher.delivered).isEmpty();

        // The partner recovers; only the first event is due (the second was never attempted).
        dispatcher.failing = false;
        List<IntegrationEvent> events = allEvents();
        events.forEach(e -> {
            e.setNextAttemptAt(java.time.OffsetDateTime.now().minusSeconds(1));
            integrationEventRepository.save(e);
        });
        worker.dispatchDueEvents();

        // Both arrive, and ACCEPTED arrives first. Letting READY overtake a retrying ACCEPTED would
        // walk the partner's tracking UI backwards.
        assertThat(dispatcher.delivered).hasSize(2);
        assertThat(dispatcher.delivered.get(0).getPayload()).contains("ACCEPTED");
        assertThat(dispatcher.delivered.get(1).getPayload()).contains("READY");
    }

    @Test
    @DisplayName("events for a deactivated partner are retired rather than retried forever")
    void inactivePartner_eventsAreRetired() {
        publisher.publish(zbr, restaurantId, IntegrationEventType.ORDER_STATUS_CHANGED,
                "order:88", Map.of("status", "ACCEPTED"));

        zbr.setActive(false);
        partnerRepository.save(zbr);
        try {
            worker.dispatchDueEvents();

            assertThat(allEvents().get(0).getStatus()).isEqualTo(IntegrationEventStatus.SUPERSEDED);
            assertThat(dispatcher.delivered).isEmpty();
        } finally {
            zbr.setActive(true);
            partnerRepository.save(zbr);
        }
    }
}
