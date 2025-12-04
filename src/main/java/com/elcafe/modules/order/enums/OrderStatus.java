package com.elcafe.modules.order.enums;

/**
 * Order Status Lifecycle:
 * PENDING → PLACED → ACCEPTED → PREPARING → READY → PICKED_UP → COMPLETED
 *                ↓       ↓
 *            REJECTED  CANCELLED
 */
public enum OrderStatus {
    PENDING,        // Payment pending
    PLACED,         // Payment completed, waiting for restaurant acceptance
    ACCEPTED,       // Restaurant accepted the order
    PREPARING,      // Kitchen is preparing the order
    READY,          // Order is ready for pickup/delivery
    PICKED_UP,      // Driver has picked up the order (for delivery)
    COMPLETED,      // Order delivered/picked up by customer
    CANCELLED,      // Cancelled by customer or admin
    REJECTED,       // Rejected by restaurant

    // Legacy statuses (kept for backward compatibility with existing orders)
    @Deprecated
    NEW,
    @Deprecated
    COURIER_ASSIGNED,
    @Deprecated
    ON_DELIVERY,
    @Deprecated
    DELIVERED
}
