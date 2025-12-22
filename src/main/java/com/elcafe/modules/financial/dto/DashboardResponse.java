package com.elcafe.modules.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    // Summary
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private BigDecimal profitMargin; // percentage

    // Period info
    private LocalDate startDate;
    private LocalDate endDate;

    // Order stats
    private OrderStats orderStats;

    // Income breakdown
    private Map<String, BigDecimal> incomeByOrderType; // DELIVERY, DINE_IN, TAKEAWAY
    private Map<String, BigDecimal> incomeByPaymentMethod; // CASH, CARD, MOBILE_PAYMENT, etc.

    // Expense breakdown
    private Map<String, BigDecimal> expensesByCategory;

    // Daily breakdown for charts
    private List<DailyStats> dailyStats;

    // Top items
    private List<TopItem> topSellingItems;

    // Comparison with previous period
    private PeriodComparison comparison;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStats {
        private Long totalOrders;
        private Long completedOrders;
        private Long cancelledOrders;
        private BigDecimal averageOrderValue;
        private Long totalItemsSold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStats {
        private LocalDate date;
        private BigDecimal income;
        private BigDecimal expenses;
        private Long orderCount;
        private BigDecimal netProfit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopItem {
        private Long productId;
        private String productName;
        private Long quantitySold;
        private BigDecimal totalRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodComparison {
        private BigDecimal incomeChange; // percentage
        private BigDecimal expenseChange; // percentage
        private BigDecimal profitChange; // percentage
        private Long orderCountChange; // percentage
        private String trend; // UP, DOWN, STABLE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayrollSummary {
        private BigDecimal totalPayroll;
        private Integer employeeCount;
        private BigDecimal averageSalary;
    }
}
