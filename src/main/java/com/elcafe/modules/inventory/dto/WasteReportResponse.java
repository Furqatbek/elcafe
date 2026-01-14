package com.elcafe.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WasteReportResponse {

    private Long restaurantId;
    private LocalDate startDate;
    private LocalDate endDate;

    // Summary statistics
    private BigDecimal totalWasteCost;
    private BigDecimal totalWasteQuantity;
    private Long recordCount;
    private String mostCommonReason;

    // Breakdown by reason
    private List<WasteByReason> wasteByReason;

    // Top wasted ingredients
    private List<TopWastedIngredient> topWastedIngredients;

    // Daily trend data
    private List<DailyWaste> dailyTrend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WasteByReason {
        private String reason;
        private String reasonLabel;
        private Long recordCount;
        private BigDecimal totalQuantity;
        private BigDecimal totalCost;
        private BigDecimal percentageOfTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopWastedIngredient {
        private Long ingredientId;
        private String ingredientName;
        private Long recordCount;
        private BigDecimal totalQuantity;
        private BigDecimal totalCost;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyWaste {
        private LocalDate date;
        private Long recordCount;
        private BigDecimal totalCost;
    }
}
