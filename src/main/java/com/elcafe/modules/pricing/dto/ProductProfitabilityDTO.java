package com.elcafe.modules.pricing.dto;

import com.elcafe.modules.pricing.enums.MenuEngineeringClass;
import com.elcafe.modules.pricing.enums.ProfitabilityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Detailed profitability analysis for a product
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductProfitabilityDTO {

    private Long productId;
    private String productName;
    private Long categoryId;
    private String categoryName;

    // Pricing
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;
    private BigDecimal ingredientCost;  // Calculated from recipe
    private BigDecimal laborCostAllocation;
    private BigDecimal overheadAllocation;
    private BigDecimal totalCost;

    // Margins
    private BigDecimal grossMargin;
    private BigDecimal grossMarginPercentage;
    private BigDecimal contributionMargin;
    private BigDecimal contributionMarginPercentage;
    private BigDecimal netMargin;
    private BigDecimal netMarginPercentage;

    // Sales Performance
    private Long unitsSold;
    private BigDecimal totalRevenue;
    private BigDecimal totalProfit;
    private BigDecimal salesVelocity;  // Units per day
    private BigDecimal revenueShare;   // % of total category revenue

    // Menu Engineering Classification (BCG Matrix for restaurants)
    private MenuEngineeringClass menuClass;  // STAR, PLOW_HORSE, PUZZLE, DOG
    private BigDecimal popularityIndex;
    private BigDecimal profitabilityIndex;

    // Status and Recommendations
    private ProfitabilityStatus status;
    private String recommendation;
    private BigDecimal suggestedPrice;
    private BigDecimal targetMarginPercentage;
}
