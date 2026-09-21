package com.elcafe.modules.inventory.enums;

public enum TransactionType {
    PURCHASE,           // Stock added from supplier
    ORDER_DEDUCTION,    // Stock deducted for customer order
    ADJUSTMENT,         // Manual stock adjustment
    WASTE,             // Stock wasted/spoiled
    RETURN,            // Stock returned from customer
    RESTOCK,           // Stock replenished
    INITIAL_STOCK,     // Initial stock entry
    TRANSFER           // Stock transferred between locations
}
