package com.elcafe.modules.marketing.event;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;

/**
 * The single decision point for firing {@link OrderCompletedEvent} (audit FUNC-15). The event chain
 * (loyalty accrual + milestones + thank-you/first-order SMS) shipped fully built but dark — nothing
 * ever published the event. This gate activates it behind {@code ORDER_COMPLETED_EVENTS_ENABLED}
 * (default OFF: enabling starts granting customer bonuses, which is a product decision).
 *
 * <p><b>Fire-once semantics — durable, not inferred.</b> An order qualifies the first time it is
 * <i>settled ∧ fully paid ∧ has a customer</i>. Qualification is NOT monotonic: a tip can raise the
 * grand total after full payment, refunds and admin payment corrections can un-pay a settled order —
 * so pre-mutation snapshots cannot express "never fired before". Instead the order carries a
 * {@code completionEventPublishedAt} marker, stamped here in the same transaction as the publish:
 * the marker and the AFTER_COMMIT event stand or fall together, and any later call for the same
 * order — from any site, in any sequence — is a no-op. This also makes the gate safe to call from
 * every place that can cross the qualification edge, including the admin payment CRUD.
 *
 * <p>Double-fires matter downstream: the bonus ledger dedupes by idempotency key, but loyalty stats
 * (totalSpent/orderCount → tier upgrades), milestone visit counters, and SMS sends do not.
 *
 * <p>Callers are all {@code @Transactional}; the customer is explicitly initialised here, in-session,
 * before the entity crosses to the AFTER_COMMIT {@code @Async} listeners (which read its name/phone
 * with no session — an uninitialised proxy there would silently drop the SMS). A null customer never
 * publishes (walk-in POS orders). Must never break the calling order flow: failures are logged and
 * swallowed.
 */
@Slf4j
@Component
public class OrderCompletionEvents {

    /** Statuses that count as "the customer's order is done" for loyalty/marketing purposes. */
    private static final Set<OrderStatus> SETTLED = EnumSet.of(OrderStatus.COMPLETED, OrderStatus.DELIVERED);

    private final MarketingEventPublisher marketingEventPublisher;
    private final OrderRepository orderRepository;
    private final boolean enabled;

    public OrderCompletionEvents(MarketingEventPublisher marketingEventPublisher,
                                 OrderRepository orderRepository,
                                 @Value("${app.marketing.order-completed-events.enabled:false}") boolean enabled) {
        this.marketingEventPublisher = marketingEventPublisher;
        this.orderRepository = orderRepository;
        this.enabled = enabled;
        if (enabled) {
            log.info("Order-completed events ENABLED: loyalty accrual and completion SMS will fire");
        }
    }

    /**
     * Publish {@link OrderCompletedEvent} if the order qualifies and has never fired before.
     * Idempotent and safe to call after ANY mutation that could affect qualification; must be called
     * inside the mutating transaction, on the managed (saved) order.
     */
    public void publishIfQualified(Order order) {
        if (!enabled || order == null) {
            return;
        }
        try {
            if (order.getCompletionEventPublishedAt() != null) {
                return; // fired before — durable, survives de-qualify/re-qualify cycles
            }
            if (order.getCustomer() == null
                    || !SETTLED.contains(order.getStatus())
                    || !order.isFullyPaid()) {
                return;
            }
            // The AFTER_COMMIT @Async listeners read the customer's name/phone with no session;
            // getId()/null-checks do NOT initialise a lazy proxy, so force it while one is open.
            Hibernate.initialize(order.getCustomer());

            // Counts settled orders only (cancelled/pending noise excluded); the count query flushes
            // the pending mutation, so the current order is included and "first order" == exactly 1.
            boolean isFirstOrder = orderRepository.countByCustomer_IdAndStatusIn(
                    order.getCustomer().getId(), SETTLED) <= 1;

            // Marker before publish, same transaction: if the save fails nothing was published (the
            // next qualifying mutation retries); once published, no future call can ever re-fire.
            order.setCompletionEventPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
            orderRepository.save(order);

            marketingEventPublisher.publishOrderCompleted(order, order.getCustomer(), isFirstOrder);
            log.info("Order-completed event published for order {} (customer {}, firstOrder={})",
                    order.getOrderNumber(), order.getCustomer().getId(), isFirstOrder);
        } catch (Exception e) {
            log.error("Failed to publish order-completed event for order {}: {}",
                    order.getId(), e.getMessage(), e);
        }
    }
}
