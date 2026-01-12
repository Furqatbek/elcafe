package com.elcafe.modules.referral.dto;

import com.elcafe.modules.referral.entity.ReferralCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralCodeResponse {

    private Long id;
    private Long restaurantId;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String code;
    private Boolean active;
    private Integer usageCount;
    private Integer maxUses;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Boolean isValid;

    public static ReferralCodeResponse from(ReferralCode referralCode) {
        return ReferralCodeResponse.builder()
                .id(referralCode.getId())
                .restaurantId(referralCode.getRestaurant() != null ? referralCode.getRestaurant().getId() : null)
                .customerId(referralCode.getCustomer() != null ? referralCode.getCustomer().getId() : null)
                .customerName(referralCode.getCustomer() != null ? referralCode.getCustomer().getName() : null)
                .customerEmail(referralCode.getCustomer() != null ? referralCode.getCustomer().getEmail() : null)
                .code(referralCode.getCode())
                .active(referralCode.getActive())
                .usageCount(referralCode.getUsageCount())
                .maxUses(referralCode.getMaxUses())
                .expiresAt(referralCode.getExpiresAt())
                .createdAt(referralCode.getCreatedAt())
                .isValid(referralCode.isValid())
                .build();
    }
}
