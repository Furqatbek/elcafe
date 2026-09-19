package com.elcafe.modules.partner.enums;

/** How a channel price is derived from the base price. */
public enum PriceAdjustmentType {

    /** Sell at the base price. The default, so an existing grant is never silently repriced. */
    NONE,

    /** {@code value} percent on top: 15 → +15%. Negative is a discount, floored at −100. */
    PERCENT,

    /** {@code value} added to the price: 500 → +500. Negative is a discount. */
    AMOUNT,

    /**
     * {@code value} <em>is</em> the price. Only meaningful as an override on a specific item, and it
     * skips rounding — someone who typed an exact price meant that exact price.
     */
    FIXED
}
