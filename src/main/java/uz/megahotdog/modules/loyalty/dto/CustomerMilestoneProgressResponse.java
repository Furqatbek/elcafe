package uz.megahotdog.modules.loyalty.dto;

import uz.megahotdog.modules.loyalty.entity.LoyaltyMilestone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
    private OffsetDateTime lastCompletionAt;
    private OffsetDateTime lastRedemptionAt;
}
