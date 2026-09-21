package com.elcafe.modules.courier.enums;

/**
 * Types of courier wallet transactions
 */
public enum WalletTransactionType {
    /**
     * Earnings from completed deliveries
     */
    DELIVERY_FEE,

    /**
     * Bonuses for performance, promotions, etc.
     */
    BONUS,

    /**
     * Tips from customers
     */
    TIP,

    /**
     * Fines for violations, damages, etc.
     */
    FINE,

    /**
     * Withdrawals to bank account
     */
    WITHDRAWAL,

    /**
     * Manual adjustment by admin
     */
    ADJUSTMENT,

    /**
     * Refund for cancelled orders
     */
    REFUND,

    /**
     * Compensation for issues
     */
    COMPENSATION
}
