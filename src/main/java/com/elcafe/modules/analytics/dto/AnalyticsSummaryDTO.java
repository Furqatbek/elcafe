package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Comprehensive analytics summary combining key metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryDTO {

    private LocalDate startDate;

    private LocalDate endDate;

    // Financial metrics
    private BigDecimal totalRevenue;

    private BigDecimal averageOrderValue;

    private BigDecimal totalCOGS;

    private BigDecimal grossProfit;

    private BigDecimal grossProfitMargin;

    private BigDecimal netProfit;

    private BigDecimal netProfitMargin;

    // Operational metrics
    private Integer totalOrders;

    private Double averagePreparationTime;

    private Double averageDeliveryTime;

    private Integer peakHourStart;

    private Integer peakHourEnd;

    // Customer metrics
    private Integer totalCustomers;

    private Integer newCustomers;

    private Double retentionRate;

    private BigDecimal averageCustomerLTV;

    private Double satisfactionScore;

    // Inventory metrics
    private Double inventoryTurnoverRatio;

    private Integer lowStockItems;
}
