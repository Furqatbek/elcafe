package com.elcafe.modules.referral.dto;

import com.elcafe.modules.referral.entity.Referral;
import com.elcafe.modules.referral.enums.ReferralStatus;
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
public class ReferralResponse {

    private Long id;
    private Long restaurantId;
    private String referralCode;
    private Long referrerId;
    private String referrerName;
    private String referrerEmail;
    private Long refereeId;
    private String refereeName;
    private String refereeEmail;
    private Long orderId;
    private ReferralStatus status;
    private Boolean referrerRewardGiven;
    private BigDecimal referrerRewardAmount;
    private RewardType referrerRewardType;
    private Boolean refereeRewardGiven;
    private BigDecimal refereeRewardAmount;
    private RewardType refereeRewardType;
    private LocalDateTime referrerRewardedAt;
    private LocalDateTime refereeRewardedAt;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static ReferralResponse from(Referral referral) {
        return ReferralResponse.builder()
                .id(referral.getId())
                .restaurantId(referral.getRestaurant() != null ? referral.getRestaurant().getId() : null)
                .referralCode(referral.getReferralCode() != null ? referral.getReferralCode().getCode() : null)
                .referrerId(referral.getReferrer() != null ? referral.getReferrer().getId() : null)
                .referrerName(referral.getReferrer() != null ? referral.getReferrer().getFirstName() + " " + referral.getReferrer().getLastName() : null)
                .referrerEmail(referral.getReferrer() != null ? referral.getReferrer().getEmail() : null)
                .refereeId(referral.getReferee() != null ? referral.getReferee().getId() : null)
                .refereeName(referral.getReferee() != null ? referral.getReferee().getFirstName() + " " + referral.getReferee().getLastName() : null)
                .refereeEmail(referral.getReferee() != null ? referral.getReferee().getEmail() : null)
                .orderId(referral.getOrder() != null ? referral.getOrder().getId() : null)
                .status(referral.getStatus())
                .referrerRewardGiven(referral.getReferrerRewardGiven())
                .referrerRewardAmount(referral.getReferrerRewardAmount())
                .referrerRewardType(referral.getReferrerRewardType())
                .refereeRewardGiven(referral.getRefereeRewardGiven())
                .refereeRewardAmount(referral.getRefereeRewardAmount())
                .refereeRewardType(referral.getRefereeRewardType())
                .referrerRewardedAt(referral.getReferrerRewardedAt())
                .refereeRewardedAt(referral.getRefereeRewardedAt())
                .createdAt(referral.getCreatedAt())
                .completedAt(referral.getCompletedAt())
                .build();
    }
}
