package com.elcafe.modules.partner.outbox;

import com.elcafe.modules.partner.entity.IntegrationEvent;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.enums.IntegrationEventType;
import com.elcafe.modules.partner.repository.IntegrationEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Queues a message for a partner.
 *
 * <p>Called from inside the transaction that made the change, deliberately: the message and the change
 * commit together or not at all. An order that rolls back cannot leave a notification saying it was
 * accepted, and an order that commits cannot lose its notification to a crash a millisecond later.
 *
 * <p>This is also why publishing must stay cheap. It is an INSERT on a hot path — no HTTP, no
 * serialization of anything large, no talking to the partner. Delivery is the worker's problem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerEventPublisher {

    private final IntegrationEventRepository integrationEventRepository;
    private final List<PartnerEventDispatcher> dispatchers;
    private final ObjectMapper objectMapper;

    /**
     * Queue an event, unless nobody can deliver it.
     *
     * <p>Joins the caller's transaction ({@code MANDATORY} would be stricter but would break callers
     * that legitimately publish outside one). Never throws: a partner notification must not be the
     * reason an order fails to save.
     *
     * @param subjectKey what this is about, e.g. {@code "order:512"} — see
     *                   {@link IntegrationEventType#isCoalescing()}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void publish(Partner partner, Long restaurantId, IntegrationEventType type,
                        String subjectKey, Object payload) {
        try {
            if (dispatchers.stream().noneMatch(dispatcher -> dispatcher.supports(partner))) {
                // No adapter for this partner, so a row here would never be delivered and would sit in
                // the queue forever looking like a backlog. Skipping keeps "pending events" meaningful.
                log.debug("No dispatcher for partner {} — skipping {} event for {}",
                        partner.getSlug(), type, subjectKey);
                return;
            }

            if (type.isCoalescing()) {
                // A newer state message makes an older undelivered one worthless. Without this, an item
                // flapping around its stock threshold queues a dozen contradictory messages and the
                // partner ends on whichever happens to land last.
                int superseded = integrationEventRepository.supersedePending(
                        partner.getId(), subjectKey, type, OffsetDateTime.now());
                if (superseded > 0) {
                    log.debug("Superseded {} pending {} event(s) for {}", superseded, type, subjectKey);
                }
            }

            integrationEventRepository.save(IntegrationEvent.builder()
                    .partnerId(partner.getId())
                    .restaurantId(restaurantId)
                    .eventType(type)
                    .subjectKey(subjectKey)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());

            log.debug("Queued {} for partner {} ({})", type, partner.getSlug(), subjectKey);
        } catch (Exception e) {
            // Best-effort by design. Telling a partner about an order is worth less than the order, so
            // a failure here is logged loudly and swallowed rather than rolled back onto the caller.
            log.error("Failed to queue {} event for partner {} ({}): {}",
                    type, partner.getSlug(), subjectKey, e.getMessage());
        }
    }
}
