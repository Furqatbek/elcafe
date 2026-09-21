package com.elcafe.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeResponse {

    private Long id;
    private Long productId;
    private String productName;
    private IngredientInfo ingredient;
    private BigDecimal quantityRequired;
    private String unit;
    private String notes;
    private Boolean optional;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientInfo {
        private Long id;
        private String name;
        private String unit;
        private String sku;
        private BigDecimal currentStock;
    }
}
