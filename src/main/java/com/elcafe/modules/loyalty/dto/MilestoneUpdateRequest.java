package com.elcafe.modules.loyalty.dto;

import com.elcafe.modules.loyalty.entity.LoyaltyMilestone;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneUpdateRequest {

    private String name;

    private String description;

    @Min(value = 1, message = "Required visits must be at least 1")
    private Integer requiredVisits;

    private LoyaltyMilestone.RewardType rewardType;

    private BigDecimal rewardValue;

    private Long rewardProductId;

    private Boolean isRepeating;

    private Boolean active;
}
