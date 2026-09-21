package com.elcafe.modules.pricing.enums;

/**
 * Menu Engineering Classification (Boston Matrix for Restaurants)
 * Used to classify menu items based on profitability and popularity
 */
public enum MenuEngineeringClass {
    STAR,        // High profitability, High popularity - Promote aggressively
    PLOW_HORSE,  // Low profitability, High popularity - Increase price or reduce cost
    PUZZLE,      // High profitability, Low popularity - Increase visibility/promotion
    DOG          // Low profitability, Low popularity - Consider removing or reengineering
}
