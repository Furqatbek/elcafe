package com.elcafe.modules.inventory.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when there is insufficient stock to complete an operation.
 */
public class InsufficientStockException extends RuntimeException {

    private final Long ingredientId;
    private final String ingredientName;
    private final BigDecimal requestedQuantity;
    private final BigDecimal availableQuantity;

    public InsufficientStockException(Long ingredientId, String ingredientName,
                                       BigDecimal requestedQuantity, BigDecimal availableQuantity) {
        super(String.format("Insufficient stock for %s (ID: %d): requested %.2f, available %.2f",
                ingredientName, ingredientId, requestedQuantity, availableQuantity));
        this.ingredientId = ingredientId;
        this.ingredientName = ingredientName;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public InsufficientStockException(String message) {
        super(message);
        this.ingredientId = null;
        this.ingredientName = null;
        this.requestedQuantity = null;
        this.availableQuantity = null;
    }

    public Long getIngredientId() {
        return ingredientId;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public BigDecimal getRequestedQuantity() {
        return requestedQuantity;
    }

    public BigDecimal getAvailableQuantity() {
        return availableQuantity;
    }
}
