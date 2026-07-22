package com.elcafe.modules.referral.dto;

import com.elcafe.modules.referral.entity.ReferralSettings;
import com.elcafe.modules.referral.enums.RewardType;
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
public class ReferralSettingsResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private Boolean programActive;
    private RewardType referrerRewardType;
    private BigDecimal referrerRewardAmount;
    private RewardType refereeRewardType;
    private BigDecimal refereeRewardAmount;
    private BigDecimal minOrderAmount;
    private Integer maxReferralsPerCustomer;
    private Integer rewardExpiresDays;
    private String termsAndConditions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReferralSettingsResponse from(ReferralSettings settings) {
        return ReferralSettingsResponse.builder()
                .id(settings.getId())
                .restaurantId(settings.getRestaurant() != null ? settings.getRestaurant().getId() : null)
                .restaurantName(settings.getRestaurant() != null ? settings.getRestaurant().getName() : null)
                .programActive(settings.getProgramActive())
                .referrerRewardType(settings.getReferrerRewardType())
                .referrerRewardAmount(settings.getReferrerRewardAmount())
                .refereeRewardType(settings.getRefereeRewardType())
                .refereeRewardAmount(settings.getRefereeRewardAmount())
                .minOrderAmount(settings.getMinOrderAmount())
                .maxReferralsPerCustomer(settings.getMaxReferralsPerCustomer())
                .rewardExpiresDays(settings.getRewardExpiresDays())
                .termsAndConditions(settings.getTermsAndConditions())
                .createdAt(settings.getCreatedAt())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }
}
