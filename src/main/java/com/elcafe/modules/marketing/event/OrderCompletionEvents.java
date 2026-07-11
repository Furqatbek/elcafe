package com.elcafe.modules.marketing.event;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * The single decision point for firing {@link OrderCompletedEvent} (audit FUNC-15). The event chain
 * (loyalty accrual + milestones + thank-you/first-order SMS) shipped fully built but dark — nothing
 * ever published the event. This gate activates it behind {@code ORDER_COMPLETED_EVENTS_ENABLED}
 * (default OFF: enabling starts granting customer bonuses, which is a product decision).
 *
 * <p><b>Fire-once semantics.</b> An order "completes", for loyalty purposes, the first time it
 * satisfies <i>settled status ∧ fully paid ∧ has a customer</i>. That moment is reached from two
 * directions — becoming settled while already paid (status writes) or becoming paid while already
 * settled (a payment landing after close) — so each call site passes the order's pre-mutation state
 * and this gate publishes only on the {@code false→true} edge of the whole predicate. Firing more
 * than once would double-count: the bonus ledger dedupes by idempotency key, but loyalty stats
 * (totalSpent/orderCount), milestone visit counters, and SMS sends do not.
 *
 * <p>Callers are all {@code @Transactional}, so the customer is initialised here (in-session) before
 * the entity crosses to the AFTER_COMMIT {@code @Async} listeners — and a null customer never
 * publishes (walk-in POS orders; the listeners dereference the customer). Must never break the
 * calling order flow: any failure is logged and swallowed.
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
     * Publish {@link OrderCompletedEvent} if this mutation just made the order qualify.
     *
     * @param order          the mutated, saved order (managed; current transaction still open)
     * @param previousStatus the order's status BEFORE the mutation, or {@code null} for a newly
     *                       created order
     * @param wasFullyPaid   whether the order was already fully paid BEFORE the mutation (pass the
     *                       current value when the mutation didn't touch payments)
     */
    public void publishIfQualified(Order order, OrderStatus previousStatus, boolean wasFullyPaid) {
        if (!enabled || order == null) {
            return;
        }
        try {
            if (order.getCustomer() == null) {
                return;
            }
            boolean qualifiesNow = SETTLED.contains(order.getStatus()) && order.isFullyPaid();
            boolean qualifiedBefore = previousStatus != null && SETTLED.contains(previousStatus) && wasFullyPaid;
            if (!qualifiesNow || qualifiedBefore) {
                return;
            }
            // Counts settled orders only (cancelled/pending noise excluded); the current order is
            // already saved in this transaction, so "first order" means this count is exactly 1.
            boolean isFirstOrder = orderRepository.countByCustomer_IdAndStatusIn(
                    order.getCustomer().getId(), SETTLED) <= 1;
            marketingEventPublisher.publishOrderCompleted(order, order.getCustomer(), isFirstOrder);
            log.info("Order-completed event published for order {} (customer {}, firstOrder={})",
                    order.getOrderNumber(), order.getCustomer().getId(), isFirstOrder);
        } catch (Exception e) {
            log.error("Failed to publish order-completed event for order {}: {}",
                    order.getId(), e.getMessage(), e);
        }
    }
}
