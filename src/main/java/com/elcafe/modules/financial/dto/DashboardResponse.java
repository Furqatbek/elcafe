package com.elcafe.modules.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    // Summary
    private BigDecimal totalIncome;
    private BigDecimal totalCOGS;
    private BigDecimal totalExpenses;
    private BigDecimal totalPayroll;
    private BigDecimal netProfit;
    private BigDecimal profitMargin; // percentage

    // Period info
    private LocalDate startDate;
    private LocalDate endDate;

    // Shift-aware time range info (shows actual query times based on business hours)
    private ShiftTimeInfo shiftTimeInfo;

    // Order stats
    private OrderStats orderStats;

    // Income breakdown
    private Map<String, BigDecimal> incomeByOrderType; // DELIVERY, DINE_IN, TAKEAWAY
    private Map<String, BigDecimal> incomeByPaymentMethod; // CASH, CARD, MOBILE_PAYMENT, etc.

    // Expense breakdown
    private Map<String, BigDecimal> expensesByCategory;

    // Daily breakdown for charts
    private List<DailyStats> dailyStats;

    // Sold items for the period
    private List<SoldItem> soldItems;

    // Comparison with previous period
    private PeriodComparison comparison;

    // Inventory alerts
    private InventoryAlerts inventoryAlerts;

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
    public static class SoldItem {
        private Long productId;
        private String productName;
        private Long quantitySold;
        private BigDecimal totalRevenue;
        private BigDecimal costPrice;
        private BigDecimal totalCost;
        private BigDecimal profit;
        private BigDecimal profitMargin; // percentage
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryAlerts {
        private Long lowStockCount;
        private Long reorderCount;
        private Long expiringCount;
        private List<LowStockItem> lowStockItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LowStockItem {
        private Long ingredientId;
        private String ingredientName;
        private BigDecimal currentStock;
        private BigDecimal minimumStock;
        private BigDecimal reorderLevel;
        private String unit;
        private String supplierName;
        private String alertLevel; // CRITICAL, LOW, REORDER
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShiftTimeInfo {
        private OffsetDateTime shiftStart;    // Actual start time for query
        private OffsetDateTime shiftEnd;      // Actual end time for query
        private LocalTime businessOpenTime;  // Restaurant opening time
        private LocalTime businessCloseTime; // Restaurant closing time
        private String description;          // Human-readable description
    }
}
