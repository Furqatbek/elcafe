package com.elcafe.modules.inventory.dto;

import com.elcafe.modules.inventory.enums.ValuationMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for inventory valuation reports
 */
public class ValuationReportDTO {

    /**
     * Comprehensive valuation comparison report
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValuationComparisonReport {
        private Long restaurantId;
        private LocalDateTime generatedAt;

        // Total values by method
        private BigDecimal fifoValue;
        private BigDecimal lifoValue;
        private BigDecimal weightedAverageValue;

        // Differences
        private BigDecimal fifoVsLifo;
        private BigDecimal fifoVsWac;
        private BigDecimal lifoVsWac;

        // Current method
        private ValuationMethod currentMethod;
        private BigDecimal currentMethodValue;

        // Recommendations
        private String recommendation;

        // Breakdown by ingredient
        private List<IngredientValuationComparison> ingredientComparisons;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientValuationComparison {
        private Long ingredientId;
        private String ingredientName;
        private String category;
        private BigDecimal currentStock;
        private String unit;

        private BigDecimal fifoValue;
        private BigDecimal lifoValue;
        private BigDecimal wacValue;
        private BigDecimal maxVariance;
        private BigDecimal variancePercent;
    }

    /**
     * Full inventory valuation report
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryValuationReport {
        private Long restaurantId;
        private ValuationMethod method;
        private LocalDateTime generatedAt;

        // Summary
        private BigDecimal totalInventoryValue;
        private int totalIngredients;
        private int ingredientsWithStock;
        private BigDecimal averageCostPerUnit;

        // Category breakdown
        private List<CategoryValuation> categoryValuations;

        // All ingredient valuations
        private List<IngredientValuationDetail> ingredientValuations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryValuation {
        private String category;
        private int ingredientCount;
        private BigDecimal totalValue;
        private BigDecimal percentageOfTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientValuationDetail {
        private Long ingredientId;
        private String ingredientName;
        private String category;
        private BigDecimal currentStock;
        private String unit;
        private BigDecimal costPerUnit;
        private BigDecimal totalValue;
        private BigDecimal percentageOfTotal;
        private int activeBatches;
        private BigDecimal oldestBatchCost;
        private BigDecimal newestBatchCost;
    }

    /**
     * Cost variance report - Actual vs Standard
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostVarianceReport {
        private Long restaurantId;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
        private LocalDateTime generatedAt;

        // Summary
        private BigDecimal totalActualCost;
        private BigDecimal totalStandardCost;
        private BigDecimal totalVariance;
        private BigDecimal variancePercent;

        // Classification
        private int favorableVariances;
        private int unfavorableVariances;
        private BigDecimal totalFavorable;
        private BigDecimal totalUnfavorable;

        // By ingredient
        private List<IngredientCostVariance> ingredientVariances;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientCostVariance {
        private Long ingredientId;
        private String ingredientName;
        private String category;

        private BigDecimal quantityConsumed;
        private String unit;

        // Costs
        private BigDecimal actualCostPerUnit;
        private BigDecimal standardCostPerUnit;
        private BigDecimal actualTotalCost;
        private BigDecimal standardTotalCost;

        // Variance
        private BigDecimal varianceAmount;
        private BigDecimal variancePercent;
        private boolean favorable;

        // Analysis
        private String varianceReason;
    }
}
