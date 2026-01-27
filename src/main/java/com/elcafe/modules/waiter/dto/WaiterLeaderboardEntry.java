package com.elcafe.modules.waiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterLeaderboardEntry {
    private Long waiterId;
    private String waiterName;
    private Integer rank;
    private BigDecimal totalRevenue;
    private Integer totalOrders;
    private BigDecimal avgRating;
    private BigDecimal avgKpiScore;
    private BigDecimal totalTips;
    private BigDecimal bonusEarned;
    // Commission / Revenue Share fields
    private BigDecimal totalCommission;
    private BigDecimal commissionPercent;
    private Boolean commissionEnabled;
}
