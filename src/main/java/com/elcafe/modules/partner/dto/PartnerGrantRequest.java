package com.elcafe.modules.partner.dto;

import com.elcafe.modules.partner.enums.PriceAdjustmentType;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * What a partner may do at one venue, and what they pay for it.
 *
 * <p>Capabilities default to the safe answer, so an operator who grants a venue without thinking about
 * them gets read-only access rather than a partner that can write into the kitchen. Pricing defaults to
 * NONE for the same reason: a grant that says nothing about price sells at the base price.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerGrantRequest {

    @Builder.Default
    private Boolean canReadMenu = true;

    @Builder.Default
    private Boolean canPushOrders = false;

    /**
     * The default channel markup at this venue: NONE, PERCENT or AMOUNT. FIXED is rejected here — an
     * absolute price makes sense for one dish, never for a whole menu.
     */
    @Builder.Default
    private PriceAdjustmentType priceAdjustmentType = PriceAdjustmentType.NONE;

    /** Percent (15 → +15%) or absolute amount (500 → +500). Negative is a discount. */
    @DecimalMin(value = "-100000", message = "Price adjustment is out of range")
    @Builder.Default
    private BigDecimal priceAdjustmentValue = BigDecimal.ZERO;

    /** Round the marked-up price to a multiple of this; 0 disables rounding. */
    @DecimalMin(value = "0", message = "Rounding cannot be negative")
    @Builder.Default
    private BigDecimal priceRounding = BigDecimal.ZERO;
}
