package com.elcafe.modules.selfservice.exception;

/**
 * Thrown when a cart operation fails.
 */
public class CartOperationException extends SelfServiceException {

    private final String operation;
    private final Long itemId;

    public CartOperationException(String operation, String message) {
        super(message);
        this.operation = operation;
        this.itemId = null;
    }

    public CartOperationException(String operation, Long itemId, String message) {
        super(message);
        this.operation = operation;
        this.itemId = itemId;
    }

    public String getOperation() {
        return operation;
    }

    public Long getItemId() {
        return itemId;
    }
}
