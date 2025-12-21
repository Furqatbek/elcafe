package com.elcafe.modules.inventory.enums;

/**
 * Reasons for ingredient cost changes.
 */
public enum CostChangeReason {
    PURCHASE,           // Cost updated from purchase order
    MANUAL_ADJUSTMENT,  // Manual cost change by user
    SUPPLIER_UPDATE,    // Supplier updated their pricing
    WAC_RECALCULATION,  // Weighted average cost recalculated
    INITIAL_SETUP,      // Initial cost when ingredient created
    IMPORT              // Imported from external system
}
