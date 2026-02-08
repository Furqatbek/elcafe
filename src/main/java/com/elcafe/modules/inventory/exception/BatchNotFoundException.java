package com.elcafe.modules.inventory.exception;

/**
 * Exception thrown when an inventory batch cannot be found.
 */
public class BatchNotFoundException extends RuntimeException {

    public BatchNotFoundException(Long batchId) {
        super(String.format("Batch not found with id: %d", batchId));
    }

    public BatchNotFoundException(String batchNumber) {
        super(String.format("Batch not found with number: %s", batchNumber));
    }

    public BatchNotFoundException(Long ingredientId, String batchNumber) {
        super(String.format("Batch not found with number %s for ingredient id: %d", batchNumber, ingredientId));
    }
}
