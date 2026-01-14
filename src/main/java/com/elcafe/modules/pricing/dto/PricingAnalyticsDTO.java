package com.elcafe.modules.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive pricing analytics response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingAnalyticsDTO {

    private LocalDate startDate;
    private LocalDate endDate;
    private Long restaurantId;

    // Overall metrics
    private BigDecimal averageMarginPercentage;
    private BigDecimal targetMarginPercentage;
    private BigDecimal marginGap;

    private BigDecimal totalRevenue;
    private BigDecimal totalCost;
    private BigDecimal totalProfit;

    // Product breakdown
    private Integer totalProducts;
    private Integer productsWithCostData;
    private Integer productsNeedingReview;
    private Integer productsUnderperforming;

    // Category breakdown
    private List<CategoryPricingDTO> categoryPricing;

    // Recommendations summary
    private Integer totalRecommendations;
    private Integer increaseRecommendations;
    private Integer decreaseRecommendations;
    private Integer maintainRecommendations;

    // Menu engineering summary
    private MenuEngineeringSummary menuEngineering;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryPricingDTO {
        private Long categoryId;
        private String categoryName;
        private Integer productCount;
        private BigDecimal averagePrice;
        private BigDecimal averageMargin;
        private BigDecimal totalRevenue;
        private BigDecimal totalProfit;
        private BigDecimal revenueShare;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuEngineeringSummary {
        private Integer stars;        // High profit, high popularity
        private Integer plowHorses;   // Low profit, high popularity
        private Integer puzzles;      // High profit, low popularity
        private Integer dogs;         // Low profit, low popularity
    }
}
