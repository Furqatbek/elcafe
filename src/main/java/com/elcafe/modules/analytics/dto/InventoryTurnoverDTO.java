package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Inventory turnover analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTurnoverDTO {

    private LocalDate startDate;

    private LocalDate endDate;

    private Double overallTurnoverRatio;

    private Double averageDaysToSellInventory;

    private BigDecimal costOfGoodsSold;

    private BigDecimal averageInventoryValue;

    private List<IngredientTurnoverDTO> ingredientTurnovers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientTurnoverDTO {

        private Long ingredientId;

        private String ingredientName;

        private String category;

        private BigDecimal quantityUsed;

        private BigDecimal averageStock;

        private Double turnoverRatio;

        private Integer daysToSellInventory;

        private BigDecimal costValue;
    }
}
