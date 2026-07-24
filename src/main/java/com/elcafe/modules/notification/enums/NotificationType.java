package com.elcafe.modules.notification.enums;

/**
 * Types of notifications in the system
 */
public enum NotificationType {
    // Order Creation
    NEW_ORDER,
    ORDER_CONFIRMED,

    // Order Acceptance
    ORDER_ACCEPTED,
    ORDER_REJECTED,

    // Order Preparation
    ORDER_PREPARING,
    START_PREPARATION,

    // Order Ready
    ORDER_READY,
    ORDER_READY_FOR_PICKUP,
    ORDER_READY_FOR_DELIVERY,

    // Courier Assignment
    COURIER_ASSIGNED,
    COURIER_ACCEPTED_ORDER,
    COURIER_DECLINED_ORDER,
    ORDER_ASSIGNED_TO_YOU,
    ORDER_NEEDS_COURIER,

    // Delivery
    ORDER_PICKED_UP,
    ORDER_ON_THE_WAY,
    ORDER_OUT_FOR_DELIVERY,

    // Completion
    ORDER_COMPLETED,
    ORDER_DELIVERED,

    // Cancellation
    ORDER_CANCELLED,

    // General
    NEW_ORDER_RECEIVED,
    NEW_ORDER_FOR_PREPARATION,
    ORDER_WILL_BE_READY_SOON,

    // Status Updates
    ORDER_STATUS_CHANGED,

    // Waiter mobile app notification taxonomy (maps 1:1 to the app's
    // NotificationType). Stored verbatim for waiter notifications so they
    // round-trip faithfully; legacy order-lifecycle types above are mapped
    // onto these on read. NEW_ORDER already exists above and is reused.
    ORDER_STATUS,
    TABLE_READY,
    KITCHEN_ALERT,
    PAYMENT_RECEIVED,
    SYSTEM_ALERT,
    SHIFT_REMINDER
}
