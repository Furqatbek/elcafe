package com.elcafe.modules.waiter.enums;

/**
 * Types of order-related events
 */
public enum OrderEventType {
    // Waiter → Kitchen
    ORDER_CREATED,
    ORDER_UPDATED,
    ORDER_SUBMITTED,
    ORDER_SUBMITTED_TO_KITCHEN,
    ITEM_ADDED,
    ITEM_REMOVED,
    ITEM_DELIVERED,

    // Kitchen → Waiter
    ORDER_COOKING,
    ORDER_READY,
    ORDER_DELAYED,
    ITEM_OUT_OF_STOCK,

    // Bill & Payment
    BILL_REQUESTED,
    PAYMENT_COMPLETED,
    ORDER_CLOSED,

    // Table Events
    TABLE_OPENED,
    TABLE_CLOSED,
    TABLE_MERGED,
    TABLE_STATUS_CHANGED,
    WAITER_ASSIGNED,
    WAITER_UNASSIGNED
}
