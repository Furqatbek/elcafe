package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Comprehensive profitability analytics including labor costs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitabilityAnalyticsDTO {

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal totalRevenue;

    private BigDecimal totalCOGS;

    private BigDecimal totalLaborCost;

    private BigDecimal totalOperatingExpenses;

    private BigDecimal grossProfit;

    private BigDecimal netProfit;

    private BigDecimal grossProfitMargin;

    private BigDecimal netProfitMargin;

    private BigDecimal laborCostPercentage;

    private BigDecimal cogsAndLaborCost;

    private BigDecimal cogsAndLaborPercentage;
}
