package com.elcafe.modules.pricing.enums;

/**
 * Status indicating profitability health of a product
 */
public enum ProfitabilityStatus {
    EXCELLENT,      // > 40% margin
    HEALTHY,        // 30-40% margin
    ACCEPTABLE,     // 20-30% margin
    NEEDS_ATTENTION, // 10-20% margin
    CRITICAL        // < 10% margin
}
