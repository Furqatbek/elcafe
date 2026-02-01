package com.elcafe.modules.pos.giftcard.enums;

/**
 * Status of a gift card.
 */
public enum GiftCardStatus {
    PENDING,    // Created but not yet activated
    ACTIVE,     // Ready to use
    REDEEMED,   // Fully used (zero balance)
    EXPIRED,    // Past expiration date
    CANCELLED,  // Cancelled/voided
    SUSPENDED   // Temporarily suspended (fraud, etc.)
}
