package com.elcafe.modules.waiter.enums;

/**
 * Status of a waiter's commission
 */
public enum CommissionStatus {
    /**
     * Commission has been calculated but not yet paid
     */
    PENDING,

    /**
     * Commission has been approved for payment
     */
    APPROVED,

    /**
     * Commission has been included in a payroll entry
     */
    PROCESSED,

    /**
     * Commission has been paid to the waiter
     */
    PAID,

    /**
     * Commission was cancelled (e.g., order was refunded)
     */
    CANCELLED
}
