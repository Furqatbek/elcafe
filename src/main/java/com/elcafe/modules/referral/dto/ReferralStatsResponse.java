package com.elcafe.modules.referral.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralStatsResponse {

    private Long totalReferrals;
    private Long pendingReferrals;
    private Long completedReferrals;
    private Long activeReferralCodes;
    private Long referralsThisMonth;
    private BigDecimal totalRewardsGiven;
    private Long topReferrerId;
    private String topReferrerName;
    private Integer topReferrerCount;
}
