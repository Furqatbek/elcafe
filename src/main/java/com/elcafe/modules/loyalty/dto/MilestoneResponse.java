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
public class MilestoneResponse {

    private Long id;
    private Long restaurantId;
    private String name;
    private String description;
    private Integer requiredVisits;
    private LoyaltyMilestone.RewardType rewardType;
    private BigDecimal rewardValue;
    private Long rewardProductId;
    private String rewardProductName;
    private Boolean isRepeating;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
