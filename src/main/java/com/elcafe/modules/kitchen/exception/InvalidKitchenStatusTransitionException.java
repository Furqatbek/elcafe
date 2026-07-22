package com.elcafe.modules.kitchen.exception;

import com.elcafe.modules.kitchen.enums.KitchenOrderStatus;

/**
 * Exception thrown when an invalid kitchen order status transition is attempted.
 */
public class InvalidKitchenStatusTransitionException extends RuntimeException {

    private final KitchenOrderStatus currentStatus;
    private final KitchenOrderStatus targetStatus;

    public InvalidKitchenStatusTransitionException(KitchenOrderStatus currentStatus, KitchenOrderStatus targetStatus) {
        super(String.format("Invalid status transition from %s to %s", currentStatus, targetStatus));
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public InvalidKitchenStatusTransitionException(String action, KitchenOrderStatus currentStatus, KitchenOrderStatus expectedStatus) {
        super(String.format("Cannot %s: current status is %s, expected %s", action, currentStatus, expectedStatus));
        this.currentStatus = currentStatus;
        this.targetStatus = expectedStatus;
    }

    public InvalidKitchenStatusTransitionException(String message) {
        super(message);
        this.currentStatus = null;
        this.targetStatus = null;
    }

    public KitchenOrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public KitchenOrderStatus getTargetStatus() {
        return targetStatus;
    }
}
