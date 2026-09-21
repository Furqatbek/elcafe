package com.elcafe.modules.inventory.enums;

/**
 * Inventory valuation methods for calculating COGS and inventory value.
 */
public enum ValuationMethod {
    /**
     * First-In-First-Out: Oldest inventory is used first.
     * Generally results in lower COGS during inflation.
     */
    FIFO,

    /**
     * Last-In-First-Out: Newest inventory is used first.
     * Generally results in higher COGS during inflation.
     */
    LIFO,

    /**
     * Weighted Average Cost: Average cost of all units in inventory.
     * Smooths out cost fluctuations over time.
     */
    WEIGHTED_AVERAGE,

    /**
     * First-Expired-First-Out: Batches closest to expiry are used first.
     * Best for perishable goods (already implemented for consumption).
     */
    FEFO
}
