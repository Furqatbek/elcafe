package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cost of Goods Sold analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class COGSAnalyticsDTO {

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal totalCOGS;

    private BigDecimal totalRevenue;

    private BigDecimal foodCostPercentage;

    private BigDecimal grossProfitMargin;

    private BigDecimal grossProfit;
}
