package com.elcafe.modules.loyalty.mapper;

import com.elcafe.modules.loyalty.dto.CustomerMilestoneProgressResponse;
import com.elcafe.modules.loyalty.dto.MilestoneResponse;
import com.elcafe.modules.loyalty.entity.LoyaltyMilestone;
import com.elcafe.modules.loyalty.entity.MilestoneRedemption;
import org.springframework.stereotype.Component;

@Component
public class MilestoneMapper {

    public MilestoneResponse toResponse(LoyaltyMilestone milestone) {
        if (milestone == null) {
            return null;
        }

        return MilestoneResponse.builder()
                .id(milestone.getId())
                .restaurantId(milestone.getRestaurant() != null ? milestone.getRestaurant().getId() : null)
                .name(milestone.getName())
                .description(milestone.getDescription())
                .requiredVisits(milestone.getRequiredVisits())
                .rewardType(milestone.getRewardType())
                .rewardValue(milestone.getRewardValue())
                .rewardProductId(milestone.getRewardProduct() != null ? milestone.getRewardProduct().getId() : null)
                .rewardProductName(milestone.getRewardProduct() != null ? milestone.getRewardProduct().getName() : null)
                .minOrderAmount(milestone.getMinOrderAmount())
                .isRepeating(milestone.getIsRepeating())
                .active(milestone.getActive())
                .createdAt(milestone.getCreatedAt())
                .updatedAt(milestone.getUpdatedAt())
                .build();
    }

    public CustomerMilestoneProgressResponse toProgressResponse(LoyaltyMilestone milestone, MilestoneRedemption redemption) {
        int currentVisits = redemption != null ? redemption.getCurrentVisits() : 0;
        int requiredVisits = milestone.getRequiredVisits();

        return CustomerMilestoneProgressResponse.builder()
                .milestoneId(milestone.getId())
                .milestoneName(milestone.getName())
                .milestoneDescription(milestone.getDescription())
                .requiredVisits(requiredVisits)
                .currentVisits(currentVisits)
                .visitsRemaining(Math.max(0, requiredVisits - currentVisits))
                .totalCompletions(redemption != null ? redemption.getTotalCompletions() : 0)
                .rewardPending(redemption != null ? redemption.getRewardPending() : false)
                .rewardType(milestone.getRewardType())
                .rewardValue(milestone.getRewardValue())
                .rewardProductName(milestone.getRewardProduct() != null ? milestone.getRewardProduct().getName() : null)
                .minOrderAmount(milestone.getMinOrderAmount())
                .lastCompletionAt(redemption != null ? redemption.getLastCompletionAt() : null)
                .lastRedemptionAt(redemption != null ? redemption.getLastRedemptionAt() : null)
                .build();
    }
}
