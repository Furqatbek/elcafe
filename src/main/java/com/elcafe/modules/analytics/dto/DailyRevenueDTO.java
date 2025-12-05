package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily revenue analytics response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenueDTO {

    private LocalDate date;

    private BigDecimal totalRevenue;

    private Integer totalOrders;

    private BigDecimal averageOrderValue;

    private BigDecimal cashRevenue;

    private BigDecimal cardRevenue;

    private BigDecimal onlineRevenue;
}
