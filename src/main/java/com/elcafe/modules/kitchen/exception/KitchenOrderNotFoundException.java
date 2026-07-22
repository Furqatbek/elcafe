package com.elcafe.modules.kitchen.exception;

/**
 * Exception thrown when a kitchen order cannot be found.
 */
public class KitchenOrderNotFoundException extends RuntimeException {

    public KitchenOrderNotFoundException(Long orderId) {
        super(String.format("Kitchen order not found with id: %d", orderId));
    }

    public KitchenOrderNotFoundException(String message) {
        super(message);
    }

    public KitchenOrderNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
