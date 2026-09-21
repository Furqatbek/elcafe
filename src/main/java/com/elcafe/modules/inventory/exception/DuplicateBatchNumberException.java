package com.elcafe.modules.inventory.exception;

/**
 * Exception thrown when attempting to create a batch with a duplicate batch number.
 */
public class DuplicateBatchNumberException extends RuntimeException {

    private final Long ingredientId;
    private final String batchNumber;

    public DuplicateBatchNumberException(Long ingredientId, String batchNumber) {
        super(String.format("Batch number '%s' already exists for ingredient id: %d", batchNumber, ingredientId));
        this.ingredientId = ingredientId;
        this.batchNumber = batchNumber;
    }

    public DuplicateBatchNumberException(String message) {
        super(message);
        this.ingredientId = null;
        this.batchNumber = null;
    }

    public Long getIngredientId() {
        return ingredientId;
    }

    public String getBatchNumber() {
        return batchNumber;
    }
}
