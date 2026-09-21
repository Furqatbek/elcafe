package com.elcafe.modules.pos.display.enums;

/**
 * Types of messages for customer display.
 */
public enum DisplayMessageType {
    ITEM_ADDED,     // New item added to order
    ITEM_REMOVED,   // Item removed from order
    ITEM_UPDATED,   // Item quantity changed
    TOTAL_UPDATE,   // Running total changed
    PAYMENT,        // Payment in progress
    PAYMENT_COMPLETE,// Payment completed
    THANK_YOU,      // Thank you message
    PROMO,          // Promotional message
    WELCOME,        // Welcome message
    IDLE            // Idle/screensaver
}
