package com.elcafe.modules.kitchen.enums;

public enum KitchenOrderStatus {
    PENDING,      // Waiting to be started
    PREPARING,    // Currently being prepared
    READY,        // Ready for pickup
    PICKED_UP,    // Picked up by courier
    CANCELLED     // Order cancelled
}
