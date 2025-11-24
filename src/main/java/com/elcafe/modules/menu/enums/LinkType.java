package com.elcafe.modules.menu.enums;

/**
 * Types of product links/recommendations
 */
public enum LinkType {
    /**
     * Recommended item based on similar products
     */
    RECOMMENDED,

    /**
     * Frequently bought together
     */
    FREQUENTLY_BOUGHT_TOGETHER,

    /**
     * Similar products
     */
    SIMILAR,

    /**
     * Upsell - higher price/premium version
     */
    UPSELL,

    /**
     * Complementary items (e.g., sauce with pizza)
     */
    COMPLEMENTARY
}
