package com.elcafe.modules.partner.zbr;

import com.elcafe.modules.order.enums.OrderStatus;

import java.util.Optional;

/**
 * The four states ZBR's order API accepts, and which of ours reach them.
 *
 * <p>Their vocabulary is smaller than ours on purpose — they track an order until a courier has it,
 * and everything after that is their own delivery flow, which we know nothing about. So most of our
 * statuses map to nothing here, and that is the correct answer rather than a gap: sending
 * {@code DELIVERED} to an endpoint that accepts four words would be rejected on every retry until
 * the message dead-lettered.
 *
 * <p>Two of ours converge on {@code DECLINED}. A rejection and a cancellation are different events
 * to a restaurant — one is "we never started", the other "we stopped" — but to their customer both
 * mean the same thing: the food is not coming and the money comes back. Their decline refunds
 * automatically.
 */
public enum ZbrOrderStatus {

    ACCEPTED,
    PREPARING,
    READY,
    DECLINED;

    /** Empty when the transition is real here but means nothing there. */
    public static Optional<ZbrOrderStatus> from(OrderStatus status) {
        if (status == null) {
            return Optional.empty();
        }
        return switch (status) {
            case ACCEPTED -> Optional.of(ACCEPTED);
            case PREPARING -> Optional.of(PREPARING);
            case READY -> Optional.of(READY);
            // We will not be serving it. Their side cancels and refunds the customer either way.
            case REJECTED, CANCELLED -> Optional.of(DECLINED);
            // Before acceptance there is nothing to report; after READY the order is in their hands
            // and their own flow tracks it. Neither is a state their API has a word for.
            case PENDING, NEW, PLACED, PICKED_UP, COURIER_ASSIGNED, ON_DELIVERY, DELIVERED, COMPLETED ->
                    Optional.empty();
        };
    }
}
