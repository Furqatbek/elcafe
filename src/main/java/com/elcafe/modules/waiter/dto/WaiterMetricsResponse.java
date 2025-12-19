package com.elcafe.modules.waiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterMetricsResponse {
    private BigDecimal totalRevenue;
    private Long totalOrders;
    private BigDecimal averageTicket;
    private List<DailyRevenueData> weeklyActivity;
    private List<RecentTransactionData> recentTransactions;
}
