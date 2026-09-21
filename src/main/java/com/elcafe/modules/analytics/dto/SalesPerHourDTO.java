package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Sales breakdown by hour of day
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesPerHourDTO {

    private Integer hour; // 0-23

    private BigDecimal totalRevenue;

    private Integer totalOrders;

    private BigDecimal averageOrderValue;

    private BigDecimal percentageOfDailyRevenue;
}
