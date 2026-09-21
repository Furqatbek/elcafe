package com.elcafe.modules.referral.dto;

import com.elcafe.modules.referral.enums.RewardType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralSettingsRequest {

    private Boolean programActive;

    @NotNull(message = "Referrer reward type is required")
    private RewardType referrerRewardType;

    @NotNull(message = "Referrer reward amount is required")
    @DecimalMin(value = "0.01", message = "Reward amount must be greater than 0")
    private BigDecimal referrerRewardAmount;

    @NotNull(message = "Referee reward type is required")
    private RewardType refereeRewardType;

    @NotNull(message = "Referee reward amount is required")
    @DecimalMin(value = "0.01", message = "Reward amount must be greater than 0")
    private BigDecimal refereeRewardAmount;

    @DecimalMin(value = "0", message = "Minimum order amount cannot be negative")
    private BigDecimal minOrderAmount;

    @Min(value = 1, message = "Max referrals must be at least 1")
    private Integer maxReferralsPerCustomer;

    @Min(value = 1, message = "Reward expiry days must be at least 1")
    private Integer rewardExpiresDays;

    private String termsAndConditions;
}
