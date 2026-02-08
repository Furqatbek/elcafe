package com.elcafe.modules.inventory.exception;

import com.elcafe.modules.inventory.entity.InventoryBatch;

/**
 * Exception thrown when an invalid batch operation is attempted.
 */
public class InvalidBatchOperationException extends RuntimeException {

    private final Long batchId;
    private final String operation;

    public InvalidBatchOperationException(String operation, Long batchId, String reason) {
        super(String.format("Cannot %s batch %d: %s", operation, batchId, reason));
        this.operation = operation;
        this.batchId = batchId;
    }

    public InvalidBatchOperationException(String operation, Long batchId, InventoryBatch.Status currentStatus) {
        super(String.format("Cannot %s batch %d: current status is %s", operation, batchId, currentStatus));
        this.operation = operation;
        this.batchId = batchId;
    }

    public InvalidBatchOperationException(String message) {
        super(message);
        this.operation = null;
        this.batchId = null;
    }

    public Long getBatchId() {
        return batchId;
    }

    public String getOperation() {
        return operation;
    }
}
