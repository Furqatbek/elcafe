package com.elcafe.modules.partner.enums;

import com.elcafe.modules.order.enums.OrderStatus;

import java.util.Optional;

/**
 * The states a partner can report back to us, and what each one means here.
 *
 * <p>Taken from ZBR's model as they documented it, and kept as their vocabulary rather than ours on
 * purpose. A partner should send the words their own system uses; translating is our job, and doing
 * it here means the translation is one table somebody can read rather than a chain of conditionals
 * spread through a service.
 *
 * <p>Two of these map to nothing, which is the honest answer rather than a gap:
 *
 * <ul>
 *   <li>{@code CREATED} — we created the order; being told it exists tells us nothing.
 *   <li>{@code REFUNDED} — a fact about money, not about the order. It is reachable on their side
 *       from DELIVERED and COMPLETED, so forcing it onto an order state would have us mark a
 *       delivered order cancelled. Accepted and acknowledged, deliberately without effect.
 * </ul>
 *
 * <p>Their {@code IN_TRANSIT} is our {@code ON_DELIVERY}: the same fact under two names, which is the
 * whole reason this table exists.
 */
public enum PartnerOrderStatus {

    CREATED(null),
    ACCEPTED(OrderStatus.ACCEPTED),
    /**
     * Their model has no REJECTED — a restaurant declining goes to CANCELLED, refunding the customer.
     * Accepted anyway, because it is the word for declining an order we have not started, and a
     * partner reading our state list will reach for it.
     */
    REJECTED(OrderStatus.REJECTED),
    PREPARING(OrderStatus.PREPARING),
    READY(OrderStatus.READY),
    COURIER_ASSIGNED(OrderStatus.COURIER_ASSIGNED),
    PICKED_UP(OrderStatus.PICKED_UP),
    IN_TRANSIT(OrderStatus.ON_DELIVERY),
    DELIVERED(OrderStatus.DELIVERED),
    COMPLETED(OrderStatus.COMPLETED),
    CANCELLED(OrderStatus.CANCELLED),
    REFUNDED(null);

    private final OrderStatus mapped;

    PartnerOrderStatus(OrderStatus mapped) {
        this.mapped = mapped;
    }

    /** Empty when the state is real on their side but moves nothing on ours. */
    public Optional<OrderStatus> toOrderStatus() {
        return Optional.ofNullable(mapped);
    }
}
