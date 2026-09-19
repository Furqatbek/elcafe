package com.elcafe.modules.partner.dto;

import com.elcafe.modules.partner.enums.PriceAdjustmentType;
import com.elcafe.modules.partner.enums.PriceRuleScope;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One exception to a venue's default channel markup. Upserted on (scope, targetId). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerPriceRuleRequest {

    @NotNull(message = "Scope is required")
    private PriceRuleScope scope;

    /** The category, product or variant this applies to, per {@link #scope}. */
    @NotNull(message = "Target ID is required")
    private Long targetId;

    /** PERCENT, AMOUNT or FIXED. NONE would be a rule that does nothing — delete it instead. */
    @NotNull(message = "Adjustment type is required")
    private PriceAdjustmentType adjustmentType;

    @NotNull(message = "Adjustment value is required")
    private BigDecimal adjustmentValue;
}
