package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Customer Lifetime Value analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLTVDTO {

    private BigDecimal averageCustomerLTV;

    private BigDecimal medianCustomerLTV;

    private BigDecimal averageOrderValue;

    private Double averagePurchaseFrequency; // orders per customer

    private Double averageCustomerLifespanDays;

    private BigDecimal totalCustomerValue;

    private Integer totalCustomersAnalyzed;

    private BigDecimal topTierCustomerLTV; // top 10%

    private BigDecimal lowTierCustomerLTV; // bottom 50%
}
