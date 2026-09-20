package com.elcafe.modules.partner.zbr;

import com.elcafe.modules.partner.dto.PartnerMenuResponse;
import com.elcafe.modules.partner.entity.IntegrationEvent;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.enums.IntegrationEventType;
import com.elcafe.modules.partner.service.PartnerMenuService;
import com.elcafe.modules.partner.service.PartnerPriceResolver;
import com.elcafe.modules.partner.service.PartnerPricingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The ZBR adapter, driven against a real HTTP server rather than a mocked one.
 *
 * <p>That choice is the point of this class. Their single-item menu update is a {@code PATCH}, and
 * the request factory Spring falls back to without Apache HttpClient 5 on the classpath cannot send
 * one — it rejects the method outright. A mocked {@code RestTemplate} never touches a request
 * factory, so it would pass happily while every availability change failed in production and order
 * status kept working: a menu quietly rotting while the integration looks healthy.
 *
 * <p>A local server on an ephemeral port costs a few milliseconds and exercises the whole path —
 * factory, method, headers, serialization.
 */
class ZbrEventDispatcherTest {

    private record Received(String method, String path, String key, String body) {}

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"success\":true,\"data\":{}}";

    private ZbrProperties properties;
    private PartnerMenuService menuService;
    private PartnerPricingService pricingService;
    private ZbrEventDispatcher dispatcher;

    private final Partner zbr = Partner.builder().name("ZBR").slug("zbr").active(true).build();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-Partner-Key"), body));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();

        zbr.setId(99L);
        properties = new ZbrProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("zbrp_test_key");

        menuService = mock(PartnerMenuService.class);
        pricingService = mock(PartnerPricingService.class);
        when(pricingService.resolverFor(anyLong(), anyLong()))
                .thenReturn(PartnerPriceResolver.passThrough());

        dispatcher = new ZbrEventDispatcher(properties, menuService, pricingService,
                new ObjectMapper(), new RestTemplateBuilder());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private IntegrationEvent event(IntegrationEventType type, String subject, String payload) {
        return IntegrationEvent.builder()
                .partnerId(99L).restaurantId(3L)
                .eventType(type).subjectKey(subject).payload(payload)
                .build();
    }

    @Test
    @DisplayName("an availability change really does go out as a PATCH")
    void availabilityChange_sendsAPatch() throws Exception {
        dispatcher.dispatch(zbr, event(IntegrationEventType.MENU_ITEM_AVAILABILITY, "product:412",
                "{\"productId\":412,\"name\":\"Osh\",\"available\":false}"));

        assertThat(received).singleElement().satisfies(request -> {
            // The whole reason this test uses a real server.
            assertThat(request.method()).isEqualTo("PATCH");
            assertThat(request.path()).isEqualTo("/api/v1/partner/venues/3/menu/items/412");
            assertThat(request.key()).isEqualTo("zbrp_test_key");
        });
    }

    @Test
    @DisplayName("an availability change leaves the price alone, because their PATCH is partial")
    void availabilityChange_omitsPrice() throws Exception {
        dispatcher.dispatch(zbr, event(IntegrationEventType.MENU_ITEM_AVAILABILITY, "product:412",
                "{\"productId\":412,\"available\":false}"));

        // Sending a price we were not asked to change would overwrite whatever they hold with our
        // idea of it — omitting the field is how their API is told to leave it.
        assertThat(received.get(0).body()).contains("\"available\":false").doesNotContain("price");
    }

    @Test
    @DisplayName("a price change sends the price and the availability together")
    void priceChange_sendsBoth() throws Exception {
        dispatcher.dispatch(zbr, event(IntegrationEventType.MENU_ITEM_CHANGED, "product:412",
                "{\"productId\":412,\"price\":34500,\"priceWithMargin\":34500,\"available\":true}"));

        assertThat(received.get(0).method()).isEqualTo("PATCH");
        assertThat(received.get(0).body()).contains("34500").contains("\"available\":true");
    }

    @Test
    @DisplayName("a size is sent on its own id, at its own absolute price, after the item")
    void variantPrices_areSentByTheirOwnId() throws Exception {
        dispatcher.dispatch(zbr, event(IntegrationEventType.MENU_ITEM_CHANGED, "product:412",
                "{\"productId\":412,\"name\":\"Osh\",\"price\":34500,\"priceWithMargin\":34500,"
                        + "\"available\":true,\"variants\":["
                        + "{\"variantId\":11,\"name\":\"Large\",\"price\":42000,"
                        + "\"priceWithMargin\":42000,\"available\":false}]}"));

        assertThat(received).hasSize(2);

        // The item first. They reorder it themselves, but a size priced against a base that is about
        // to move is a bad enough failure not to depend on somebody else's guarantee for.
        assertThat(received.get(0).path()).isEqualTo("/api/v1/partner/venues/3/menu/items/412");
        assertThat(received.get(0).body()).contains("34500").doesNotContain("externalVariantId");

        // Then the size, on its own field: their product ids and variant ids are separate sequences,
        // and an id that meant either would eventually reprice the wrong dish.
        assertThat(received.get(1).method()).isEqualTo("PATCH");
        assertThat(received.get(1).path()).isEqualTo("/api/v1/partner/venues/3/menu/items/412");
        assertThat(received.get(1).body())
                .contains("\"externalVariantId\":\"11\"")
                // Absolute, not the delta our own model stores — what the Large costs, which is also
                // what our order check will compare their expectedTotal against.
                .contains("42000")
                // A Large sold out while Regular is fine is the half that puts food in front of a
                // customer who cannot have it, so it travels with the price.
                .contains("\"available\":false");
    }

    @Test
    @DisplayName("an order status goes to their reference, in their vocabulary")
    void orderStatus_usesTheirReferenceAndWords() throws Exception {
        dispatcher.dispatch(zbr, event(IntegrationEventType.ORDER_STATUS_CHANGED, "order:512",
                "{\"externalOrderId\":\"FD-20260919-A7K2M9\",\"status\":\"ACCEPTED\",\"reason\":null}"));

        // Menus are ours and use our ids; orders were created on their side and keep their name.
        assertThat(received.get(0).path())
                .isEqualTo("/api/v1/partner/orders/FD-20260919-A7K2M9/status");
        assertThat(received.get(0).body()).contains("\"status\":\"ACCEPTED\"");
    }

    @Test
    @DisplayName("every kitchen state set here reaches them — the echo rule is about origin, not state")
    void kitchenStates_allReachThePartner() throws Exception {
        // The tempting optimisation is "do not send kitchen states to a kitchen", and it is wrong.
        // Those states are only redundant when the restaurant set them in the partner's own app, and
        // that case is already handled upstream by not echoing a change back to whoever reported it.
        // A restaurant working from our till instead is the other half, and suppressing by state
        // would leave their screen showing an order still waiting while ours showed it cooking —
        // mid-service, with no way for the two to converge.
        for (String status : List.of("ACCEPTED", "PREPARING", "READY")) {
            dispatcher.dispatch(zbr, event(IntegrationEventType.ORDER_STATUS_CHANGED, "order:512",
                    "{\"externalOrderId\":\"FD-1\",\"status\":\"" + status + "\",\"reason\":null}"));
        }

        assertThat(received).hasSize(3);
        assertThat(received.get(0).body()).contains("\"status\":\"ACCEPTED\"");
        assertThat(received.get(1).body()).contains("\"status\":\"PREPARING\"");
        assertThat(received.get(2).body()).contains("\"status\":\"READY\"");
    }

    @Test
    @DisplayName("a cancellation reads as a decline, which is what it means to their customer")
    void cancellation_becomesDeclined() throws Exception {
        dispatcher.dispatch(zbr, event(IntegrationEventType.ORDER_STATUS_CHANGED, "order:512",
                "{\"externalOrderId\":\"FD-1\",\"status\":\"CANCELLED\",\"reason\":\"Out of lamb\"}"));

        assertThat(received.get(0).body())
                .contains("\"status\":\"DECLINED\"")
                // Their side shows this to the customer, so it has to travel.
                .contains("Out of lamb");
    }

    @Test
    @DisplayName("a status their API has no word for is not sent at all")
    void unmappableStatus_sendsNothing() throws Exception {
        dispatcher.dispatch(zbr, event(IntegrationEventType.ORDER_STATUS_CHANGED, "order:512",
                "{\"externalOrderId\":\"FD-1\",\"status\":\"DELIVERED\"}"));

        // Their order API knows four words. Posting DELIVERED would be refused on every retry until
        // the message dead-lettered, turning an ordinary order into an operator alert.
        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("a reason longer than their limit is trimmed rather than rejected")
    void longReason_isTrimmed() throws Exception {
        String essay = "x".repeat(900);
        dispatcher.dispatch(zbr, event(IntegrationEventType.ORDER_STATUS_CHANGED, "order:512",
                "{\"externalOrderId\":\"FD-1\",\"status\":\"REJECTED\",\"reason\":\"" + essay + "\"}"));

        assertThat(received.get(0).body()).doesNotContain("x".repeat(501));
    }

    @Test
    @DisplayName("an item they have never heard of is not retried forever")
    void notFound_isSwallowed() {
        responseStatus = 404;
        responseBody = "{\"success\":false,\"message\":\"no such item\"}";

        // Asking again reaches the same answer, so throwing would spend ten retries to dead-letter
        // something that was only ever a stale id.
        assertThatCode(() -> dispatcher.dispatch(zbr,
                event(IntegrationEventType.MENU_ITEM_AVAILABILITY, "product:9001",
                        "{\"productId\":9001,\"available\":false}")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a rate limit is handed back to the outbox to retry")
    void rateLimited_throws() {
        responseStatus = 429;
        responseBody = "{\"success\":false,\"message\":\"slow down\"}";

        assertThatThrownBy(() -> dispatcher.dispatch(zbr,
                event(IntegrationEventType.MENU_ITEM_AVAILABILITY, "product:412",
                        "{\"productId\":412,\"available\":false}")))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    @DisplayName("a markup change becomes one bulk call carrying the new prices")
    void venuePricingChange_pushesTheWholeMenu() throws Exception {
        when(menuService.getMenu(anyLong(), any())).thenReturn(menuOf(
                product(1L, "Osh", "34500"), product(2L, "Lagman", "32000")));
        responseBody = "{\"success\":true,\"data\":{\"updated\":2,\"unknownItemIds\":[],\"rejected\":[]}}";

        dispatcher.dispatch(zbr, event(IntegrationEventType.MENU_PRICES_CHANGED, "menu:3",
                "{\"restaurantId\":3,\"reason\":\"CHANNEL_PRICING_CHANGED\"}"));

        // Their API cannot read our menu back, so "your prices moved" has to say what they now are.
        assertThat(received).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/api/v1/partner/venues/3/menu/items");
            assertThat(request.body())
                    .contains("\"externalItemId\":\"1\"").contains("34500")
                    .contains("\"externalItemId\":\"2\"").contains("32000");
        });
    }

    @Test
    @DisplayName("a venue-wide push carries the sizes too, not only the items")
    void venuePricingChange_includesVariants() throws Exception {
        PartnerMenuResponse.Product osh = product(1L, "Osh", "34500");
        osh.setVariants(List.of(PartnerMenuResponse.Variant.builder()
                .id(11L).name("Large").price(new BigDecimal("42000"))
                .priceWithMargin(new BigDecimal("42000")).available(true).build()));
        when(menuService.getMenu(anyLong(), any())).thenReturn(menuOf(osh));

        dispatcher.dispatch(zbr, event(IntegrationEventType.MENU_PRICES_CHANGED, "menu:3",
                "{\"restaurantId\":3,\"reason\":\"CHANNEL_PRICING_CHANGED\"}"));

        // A markup moves every size as well. Sending only the items would have this path put back,
        // once per markup change, the staleness the single-item path now fixes — and on the day a
        // venue repriced its whole menu, which is the worst day for a catalogue to be half right.
        assertThat(received).singleElement().satisfies(request -> assertThat(request.body())
                .contains("\"externalItemId\":\"1\"").contains("34500")
                .contains("\"externalVariantId\":\"11\"").contains("42000"));
    }

    @Test
    @DisplayName("a venue-wide push is split into batches rather than one enormous call")
    void venuePricingChange_batches() throws Exception {
        properties.setBulkBatchSize(2);
        List<PartnerMenuResponse.Product> many = new ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            many.add(product(id, "Item " + id, "10000"));
        }
        when(menuService.getMenu(anyLong(), any()))
                .thenReturn(menuOf(many.toArray(new PartnerMenuResponse.Product[0])));

        dispatcher.dispatch(zbr, event(IntegrationEventType.MENU_PRICES_CHANGED, "menu:3", "{}"));

        // Five items at two per call. A request carrying a thousand rows times out slowly and
        // retries expensively; their partial-success response means a smaller call loses less.
        assertThat(received).hasSize(3);
    }

    @Test
    @DisplayName("without a key and a URL the dispatcher does not claim the partner at all")
    void unconfigured_doesNotClaimThePartner() {
        assertThat(dispatcher.supports(zbr)).isTrue();

        ZbrProperties blank = new ZbrProperties();
        ZbrEventDispatcher unconfigured = new ZbrEventDispatcher(blank, menuService, pricingService,
                new ObjectMapper(), new RestTemplateBuilder());

        // The publisher asks before queueing anything, so an environment with no credentials builds
        // no backlog it could never deliver.
        assertThat(unconfigured.supports(zbr)).isFalse();
    }

    private PartnerMenuResponse.Product product(long id, String name, String price) {
        return PartnerMenuResponse.Product.builder()
                .id(id).name(name).price(new BigDecimal(price)).priceWithMargin(new BigDecimal(price))
                .available(true).build();
    }

    private PartnerMenuResponse menuOf(PartnerMenuResponse.Product... products) {
        return PartnerMenuResponse.builder()
                .restaurantId(3L).restaurantName("Demo")
                .categories(List.of(PartnerMenuResponse.Category.builder()
                        .id(1L).name("Main").products(List.of(products)).build()))
                .build();
    }
}
