package com.elcafe.modules.courier.enums;

/**
 * Enum representing the current status of a courier
 */
public enum CourierStatus {
    /**
     * Courier is offline and not accepting orders
     */
    OFFLINE,

    /**
     * Courier is online and available to accept new orders
     */
    ONLINE,

    /**
     * Courier is currently delivering an order
     */
    ON_DELIVERY,

    /**
     * Courier is busy (e.g., on break, handling issue) but still online
     */
    BUSY
}
