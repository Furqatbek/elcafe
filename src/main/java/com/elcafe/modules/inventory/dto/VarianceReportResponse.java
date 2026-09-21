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
public class VarianceReportResponse {

    private Long restaurantId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalVarianceValue;
    private Integer totalVarianceCount;
    private List<VarianceByReason> varianceByReason;
    private List<TopVarianceIngredient> topVarianceIngredients;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VarianceByReason {
        private String reason;
        private Long count;
        private BigDecimal totalValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopVarianceIngredient {
        private Long ingredientId;
        private String ingredientName;
        private Long varianceCount;
        private BigDecimal totalVarianceValue;
    }
}
