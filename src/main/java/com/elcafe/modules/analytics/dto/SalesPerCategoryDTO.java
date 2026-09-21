package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Sales breakdown by product category
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesPerCategoryDTO {

    private Long categoryId;

    private String categoryName;

    private BigDecimal totalRevenue;

    private Integer totalItemsSold;

    private BigDecimal percentageOfTotalRevenue;

    private BigDecimal averageItemPrice;

    private Integer numberOfProducts;
}
