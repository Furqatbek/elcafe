package com.elcafe.modules.pricing.enums;

/**
 * Pricing strategies available in the system
 */
public enum PricingStrategy {
    COST_PLUS,          // Base cost + target margin
    COMPETITION_BASED,  // Based on market/competitor pricing
    VALUE_BASED,        // Based on perceived customer value
    DEMAND_BASED,       // Dynamic based on demand patterns
    BUNDLE,             // Combo/bundle pricing optimization
    PSYCHOLOGICAL       // Price anchoring ($9.99 vs $10)
}
