package com.elcafe.modules.loyalty.dto;

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
public class CustomerLoyaltyResponse {

    private Long id;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private BigDecimal currentBalance;
    private BigDecimal lifetimeEarned;
    private BigDecimal lifetimeSpent;
    private TierInfo tier;
    private BigDecimal totalSpent;
    private Integer orderCount;
    private LocalDateTime lastOrderDate;
    private Boolean firstOrderBonusClaimed;
    private Integer birthdayBonusClaimedYear;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierInfo {
        private Long id;
        private String name;
        private Integer level;
        private BigDecimal bonusMultiplier;
        private String color;
        private String icon;
        private String benefitsDescription;
    }
}
