package com.elcafe.modules.loyalty.dto;

import com.elcafe.modules.loyalty.entity.LoyaltyMilestone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerMilestoneProgressResponse {

    private Long milestoneId;
    private String milestoneName;
    private String milestoneDescription;
    private Integer requiredVisits;
    private Integer currentVisits;
    private Integer visitsRemaining;
    private Integer totalCompletions;
    private Boolean rewardPending;
    private LoyaltyMilestone.RewardType rewardType;
    private BigDecimal rewardValue;
    private String rewardProductName;
    private BigDecimal minOrderAmount;
    private LocalDateTime lastCompletionAt;
    private LocalDateTime lastRedemptionAt;
}
