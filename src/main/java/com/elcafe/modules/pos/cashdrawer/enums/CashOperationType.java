package com.elcafe.modules.pos.cashdrawer.enums;

/**
 * Types of cash drawer operations.
 */
public enum CashOperationType {
    OPEN,       // Drawer opened (no cash movement)
    CASH_IN,    // Cash received from customer
    CASH_OUT,   // Change given to customer
    PAID_IN,    // Non-sale cash added (e.g., float)
    PAID_OUT,   // Non-sale cash removed (e.g., vendor payment)
    DROP,       // Cash removed to safe/bank
    PICKUP,     // Manager cash pickup
    CLOSE,      // End of shift drawer close
    COUNT,      // Cash count performed
    ADJUSTMENT  // Manual adjustment
}
