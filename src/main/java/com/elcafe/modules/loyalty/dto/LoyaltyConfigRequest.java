package com.elcafe.modules.loyalty.dto;

import com.elcafe.modules.loyalty.entity.LoyaltyConfig;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Upsert payload for the per-restaurant (or global, when restaurantId is null)
 * loyalty configuration. All scalar fields are optional — only supplied
 * values overwrite the existing config.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyConfigRequest {

    private Long restaurantId;

    private LoyaltyConfig.BonusRateType bonusRateType;

    @PositiveOrZero
    private BigDecimal bonusRateValue;

    @PositiveOrZero
    private Integer maxBonusPaymentPercentage;

    @PositiveOrZero
    private BigDecimal minOrderAmountForBonus;

    @PositiveOrZero
    private BigDecimal birthdayBonusAmount;

    @PositiveOrZero
    private BigDecimal firstOrderBonusAmount;

    @PositiveOrZero
    private BigDecimal reactivationBonusAmount;

    @PositiveOrZero
    private Integer reactivationDaysThreshold;

    @PositiveOrZero
    private Integer bonusExpiryDays;

    /** V182: welcome bonus credited once when a guest completes registration. */
    @PositiveOrZero
    private BigDecimal registrationBonusAmount;

    /** V182: master switch for the welcome bonus — it grants real money, so it is off until turned on. */
    private Boolean registrationBonusEnabled;

    private Boolean enabled;
}
