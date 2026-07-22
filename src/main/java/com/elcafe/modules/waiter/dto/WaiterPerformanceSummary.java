package com.elcafe.modules.waiter.dto;

import com.elcafe.modules.waiter.entity.WaiterPerformance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterPerformanceSummary {
    private Long waiterId;
    private String waiterName;
    private LocalDate startDate;
    private LocalDate endDate;

    // Aggregated metrics
    private Integer totalOrders;
    private Integer totalTablesServed;
    private Integer totalCustomersServed;
    private BigDecimal totalRevenue;
    private BigDecimal totalTips;
    private BigDecimal avgTicketValue;

    // Service quality
    private Integer avgServiceTimeMinutes;
    private Integer complaintsCount;
    private Integer complimentsCount;
    private BigDecimal avgCustomerRating;

    // KPI
    private BigDecimal avgKpiScore;
    private BigDecimal totalBonusEarned;
    private Integer workingDays;

    // Commission / Revenue Share
    private BigDecimal totalCommission;
    private BigDecimal commissionPercent;
    private Boolean commissionEnabled;

    // Daily breakdown
    private List<WaiterPerformance> dailyPerformances;
}
