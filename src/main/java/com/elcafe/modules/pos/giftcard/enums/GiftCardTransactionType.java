package com.elcafe.modules.pos.giftcard.enums;

/**
 * Types of gift card transactions.
 */
public enum GiftCardTransactionType {
    PURCHASE,       // Initial purchase/activation
    RELOAD,         // Add more value
    REDEMPTION,     // Use to pay for order
    REFUND,         // Refund back to card
    ADJUSTMENT,     // Manual adjustment
    EXPIRATION,     // Expired balance write-off
    TRANSFER_IN,    // Balance transferred in
    TRANSFER_OUT    // Balance transferred out
}
