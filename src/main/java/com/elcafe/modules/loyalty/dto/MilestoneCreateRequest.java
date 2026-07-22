package com.elcafe.modules.loyalty.dto;

import com.elcafe.modules.loyalty.entity.LoyaltyMilestone;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class MilestoneCreateRequest {

    @NotBlank(message = "Milestone name is required")
    private String name;

    private String description;

    @NotNull(message = "Required visits count is required")
    @Min(value = 1, message = "Required visits must be at least 1")
    private Integer requiredVisits;

    @NotNull(message = "Reward type is required")
    private LoyaltyMilestone.RewardType rewardType;

    private BigDecimal rewardValue;

    private Long rewardProductId;

    private BigDecimal minOrderAmount;

    @Builder.Default
    private Boolean isRepeating = true;
}
