package com.elcafe.modules.order.enums;

public enum OrderStatus {
    PENDING,
    NEW,
    PLACED,
    ACCEPTED,
    REJECTED,
    PREPARING,
    READY,
    PICKED_UP,
    COURIER_ASSIGNED,
    ON_DELIVERY,
    DELIVERED,
    COMPLETED,
    CANCELLED;

    /**
     * Has food been committed to? The cancellation cutoff, in one place.
     *
     * <p>Up to and including {@code ACCEPTED} nothing has been cooked, so a customer changing their
     * mind costs the venue nothing and cancelling is free. From {@code PREPARING} onwards ingredients
     * have been used and a cook's time has been spent, and a full refund means the restaurant pays
     * for a meal nobody eats. That is the line.
     *
     * <p>Written once and shared because it had already been written twice, differently: the consumer
     * path listed four states it would refuse and quietly allowed a customer to cancel an order a
     * courier was already carrying. A cutoff spelled out as an enumeration at each call site is a
     * cutoff that drifts.
     *
     * <p><b>This governs customers, not staff.</b> A manager cancelling a half-cooked order — a fire,
     * an ingredient found spoiled, a customer at the counter — goes through
     * {@code OrderService.cancelOrder} and is bound only by the transition rules. Their judgement is
     * the point; this line exists to stop someone else spending their food for them.
     */
    public boolean kitchenHasStarted() {
        return switch (this) {
            case PENDING, NEW, PLACED, ACCEPTED, REJECTED, CANCELLED -> false;
            case PREPARING, READY, PICKED_UP, COURIER_ASSIGNED, ON_DELIVERY, DELIVERED, COMPLETED -> true;
        };
    }
}
