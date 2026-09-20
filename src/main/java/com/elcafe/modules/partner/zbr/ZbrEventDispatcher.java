package com.elcafe.modules.partner.zbr;

import com.elcafe.modules.partner.dto.PartnerMenuResponse;
import com.elcafe.modules.partner.entity.IntegrationEvent;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.outbox.PartnerEventDispatcher;
import com.elcafe.modules.partner.service.PartnerMenuService;
import com.elcafe.modules.partner.service.PartnerPricingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Speaks ZBR.
 *
 * <p>Everything ZBR-shaped lives here and nowhere else. The outbox holds messages durably, retries
 * them and gives up on them; it knows nothing about HTTP, about their header, or about the fact that
 * their menu is addressed by our product ids while their orders are addressed by their reference.
 * Onboarding the next aggregator is another class like this one.
 *
 * <p><b>Whose id names what.</b> Their contract splits it by ownership, which is worth stating
 * because it looks inconsistent until you see the rule: menus are ours, so their menu endpoints take
 * our venue and product ids; orders were created on their side and pushed to us, so their order
 * endpoint takes their {@code FD-...} reference. We already hold that as {@code externalOrderId},
 * because it is what they sent us when they pushed the order.
 *
 * <p><b>Failures.</b> Throwing hands the message back to the outbox, which backs off and retries
 * until the attempts are spent and then dead-letters it. That is right for a timeout, a 5xx or a
 * 429. It is wrong for a 404 or a 422 — a menu item they have never heard of will not start existing
 * because we asked again — so those are logged and swallowed, which marks the message delivered
 * rather than burning ten retries to reach the same conclusion.
 */
@Slf4j
@Component
public class ZbrEventDispatcher implements PartnerEventDispatcher {

    private static final String KEY_HEADER = "X-Partner-Key";
    private static final String SLUG = "zbr";

    private final ZbrProperties properties;
    private final PartnerMenuService partnerMenuService;
    private final PartnerPricingService pricingService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ZbrEventDispatcher(ZbrProperties properties,
                              PartnerMenuService partnerMenuService,
                              PartnerPricingService pricingService,
                              ObjectMapper objectMapper,
                              RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.partnerMenuService = partnerMenuService;
        this.pricingService = pricingService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .requestFactory(() -> patchCapableFactory(properties))
                .build();
    }

    /**
     * The JDK's HTTP client, chosen explicitly rather than left to auto-detection.
     *
     * <p>Their single-item menu update is a {@code PATCH}, and the factory Spring falls back to when
     * no Apache HttpClient 5 is on the classpath is {@code SimpleClientHttpRequestFactory} — built on
     * {@code HttpURLConnection}, which rejects PATCH outright with "Invalid HTTP method". Nothing
     * here pulls in HttpClient 5 (the 4.x on the classpath arrives transitively and Spring 6 does not
     * look for it), so the default would fail on every availability change and succeed on everything
     * else — the worst shape of bug, since order status would work and the menu would quietly rot.
     *
     * <p>Java's own client handles PATCH and needs no dependency. Timeouts go on the factory rather
     * than the builder because supplying a factory bypasses the builder's settings.
     */
    private static ClientHttpRequestFactory patchCapableFactory(ZbrProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                        .build());
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return factory;
    }

    /**
     * Claims ZBR only once a key and a URL exist.
     *
     * <p>The publisher asks this before writing anything, so an environment with no credentials
     * queues nothing at all rather than building a backlog it could never deliver — and "pending
     * events" on the Partners page stays a number that means something.
     */
    @Override
    public boolean supports(Partner partner) {
        return partner != null && SLUG.equals(partner.getSlug()) && properties.isConfigured();
    }

    @Override
    public void dispatch(Partner partner, IntegrationEvent event) throws Exception {
        JsonNode payload = objectMapper.readTree(event.getPayload());

        switch (event.getEventType()) {
            case ORDER_STATUS_CHANGED -> reportOrderStatus(event, payload);
            case MENU_ITEM_AVAILABILITY -> patchItem(event, payload.path("productId").asLong(),
                    null, payload.path("available").asBoolean());
            case MENU_ITEM_CHANGED -> {
                warnAboutUndeliverableVariants(event, payload);
                patchItem(event, payload.path("productId").asLong(),
                        priceOf(payload), payload.path("available").asBoolean());
            }
            case MENU_PRICES_CHANGED -> pushWholeMenu(partner, event.getRestaurantId());
        }
    }

    /**
     * One order, one call, addressed by their reference.
     *
     * <p>Most transitions never get here. Their API has four words for an order's life and ours has
     * thirteen; a {@code DELIVERED} has nothing to say to an endpoint that does not know the word,
     * and retrying it ten times before dead-lettering would turn a normal order into an operator
     * alert. Those are logged and treated as delivered, because there was nothing to deliver.
     */
    private void reportOrderStatus(IntegrationEvent event, JsonNode payload) {
        String status = payload.path("status").asText();
        Optional<ZbrOrderStatus> theirs =
                ZbrOrderStatus.from(com.elcafe.modules.order.enums.OrderStatus.valueOf(status));
        if (theirs.isEmpty()) {
            log.debug("Order status {} has no equivalent at ZBR — nothing to send for {}",
                    status, event.getSubjectKey());
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", theirs.get().name());
        // Shown to their customer when we decline, so it carries whatever a staff member typed.
        body.put("reason", trimmedReason(payload.path("reason").asText(null)));

        send(HttpMethod.POST,
                "/api/v1/partner/orders/" + payload.path("externalOrderId").asText() + "/status",
                body, event);
    }

    /**
     * Their menu API addresses items. It has no notion of a size.
     *
     * <p>We publish both halves of a variant — its channel price and whether it is in stock — and
     * there is nowhere to put either. {@code PATCH .../menu/items/{id}} takes {@code price} and
     * {@code available} for one item id; the bulk endpoint takes {@code externalItemId}. So a Large
     * going up in price, or selling out while Regular is fine, reaches ZBR only on their next full
     * pull of the menu.
     *
     * <p>Until that pull their customer is quoted the old price and our {@code expectedTotal} check
     * refuses the order, or orders a size nobody can make and our variant check refuses that. Both
     * refusals are correct and neither should ever have reached a customer — which is precisely what
     * the outbox exists to prevent, so it is not allowed to pass in silence. The fix is theirs to
     * make: an id we can address a size by. Until they have one, this is the record that we tried.
     */
    private void warnAboutUndeliverableVariants(IntegrationEvent event, JsonNode payload) {
        JsonNode variants = payload.path("variants");
        if (!variants.isArray() || variants.isEmpty()) {
            return;
        }
        log.warn("Product {} at venue {} has {} size(s); ZBR's menu API addresses items only, so their "
                        + "prices and stock were not sent and stay stale until ZBR re-pull the menu",
                payload.path("productId").asLong(), event.getRestaurantId(), variants.size());
    }

    /** One item. {@code null} price means "leave it alone" — their PATCH is a partial update. */
    private void patchItem(IntegrationEvent event, long productId, BigDecimal price, boolean available) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (price != null) {
            body.put("price", price);
        }
        body.put("available", available);

        send(HttpMethod.PATCH,
                "/api/v1/partner/venues/" + event.getRestaurantId() + "/menu/items/" + productId,
                body, event);
    }

    /**
     * Every live item at this partner's new channel prices, in batches.
     *
     * <p>This is what a venue-wide markup change becomes. Their API cannot read our menu back, so
     * "your prices moved, re-read them" has nowhere to land — we have to say what the new ones are.
     *
     * <p>The prices come from {@link PartnerMenuService} through the same resolver that prices the
     * menu endpoint and the order push. That is the point: what we push here, what we serve when they
     * pull, and what we charge when they order are one calculation, so they cannot drift into three
     * answers and start failing each other's totals.
     */
    private void pushWholeMenu(Partner partner, Long restaurantId) {
        PartnerMenuResponse menu = partnerMenuService.getMenu(restaurantId,
                pricingService.resolverFor(partner.getId(), restaurantId));

        List<Map<String, Object>> items = new ArrayList<>();
        for (PartnerMenuResponse.Category category : menu.getCategories()) {
            for (PartnerMenuResponse.Product product : category.getProducts()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("externalItemId", String.valueOf(product.getId()));
                item.put("price", product.getPrice());
                item.put("available", Boolean.TRUE.equals(product.getAvailable()));
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            log.info("Venue {} has no live items — nothing to push to ZBR", restaurantId);
            return;
        }

        for (int from = 0; from < items.size(); from += properties.getBulkBatchSize()) {
            List<Map<String, Object>> batch =
                    items.subList(from, Math.min(from + properties.getBulkBatchSize(), items.size()));
            ResponseEntity<String> response = exchange(HttpMethod.POST,
                    "/api/v1/partner/venues/" + restaurantId + "/menu/items", batch);
            logPartialSuccess(restaurantId, response);
        }
    }

    /**
     * Their bulk endpoint reports partial success rather than rolling back, and they are right to:
     * four hundred prices must not be lost because three items were deleted here last week. But an
     * unknown id is drift between our menu and their catalogue, and drift nobody sees is drift that
     * grows — so the ids come out into the log where someone can reconcile them.
     */
    private void logPartialSuccess(Long restaurantId, ResponseEntity<String> response) {
        try {
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            JsonNode unknown = data.path("unknownItemIds");
            if (unknown.isArray() && !unknown.isEmpty()) {
                log.warn("ZBR does not recognise {} item(s) from venue {} — they are on our menu and "
                                + "not in their catalogue: {}", unknown.size(), restaurantId, unknown);
            }
            log.info("Pushed menu prices for venue {} to ZBR: {} updated",
                    restaurantId, data.path("updated").asInt());
        } catch (Exception e) {
            // A response we cannot parse is not a delivery failure — they answered 2xx.
            log.warn("Could not read ZBR's bulk menu response for venue {}: {}",
                    restaurantId, e.getMessage());
        }
    }

    private void send(HttpMethod method, String path, Object body, IntegrationEvent event) {
        try {
            exchange(method, path, body);
            log.debug("Delivered {} to ZBR ({})", event.getEventType(), event.getSubjectKey());
        } catch (HttpClientErrorException e) {
            if (isPermanent(e)) {
                // Asking again will reach the same answer. Burning ten retries to get there turns a
                // stale id into an operator alert, which is not what a dead-letter queue is for.
                log.warn("ZBR refused {} for {} permanently ({}): {}",
                        event.getEventType(), event.getSubjectKey(), e.getStatusCode(),
                        e.getResponseBodyAsString());
                return;
            }
            throw e;
        }
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Their header, not Authorization: that one carries user sessions on their side, and a
        // partner key sent in it would be parsed as a session token and fail confusingly.
        headers.set(KEY_HEADER, properties.getApiKey());

        return restTemplate.exchange(properties.getBaseUrl() + path, method,
                new HttpEntity<>(body, headers), String.class);
    }

    /**
     * Which refusals are worth retrying.
     *
     * <p>{@code 404} is an item or order they do not have, {@code 422} is a request they understood
     * and will not apply, {@code 403} is a capability our grant lacks. None of those change because
     * we wait. {@code 429} does, and {@code 401} might — a key rotated on their side and not yet
     * here is worth an operator seeing in the dead-letter queue rather than silently dropping.
     */
    private boolean isPermanent(HttpClientErrorException e) {
        return switch (e.getStatusCode().value()) {
            case 403, 404, 409, 422 -> true;
            default -> false;
        };
    }

    private BigDecimal priceOf(JsonNode payload) {
        JsonNode price = payload.path("price");
        return price.isMissingNode() || price.isNull() ? null : price.decimalValue();
    }

    /** Their limit is 500 characters, and a rejected call over one long note helps nobody. */
    private String trimmedReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }
}
