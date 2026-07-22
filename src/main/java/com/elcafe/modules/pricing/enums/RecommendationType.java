package com.elcafe.modules.pricing.enums;

/**
 * Types of pricing recommendations
 */
public enum RecommendationType {
    INCREASE,       // Recommend price increase
    DECREASE,       // Recommend price decrease
    MAINTAIN,       // Current price is optimal
    REVIEW,         // Needs manual review
    DISCONTINUE     // Consider removing from menu
}
