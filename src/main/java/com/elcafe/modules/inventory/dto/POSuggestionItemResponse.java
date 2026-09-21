package com.elcafe.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POSuggestionItemResponse {

    private Long ingredientId;
    private String ingredientName;
    private String sku;
    private String unit;
    private BigDecimal currentStock;
    private BigDecimal minimumStock;
    private BigDecimal reorderLevel;
    private BigDecimal suggestedQuantity;
    private BigDecimal costPerUnit;
    private BigDecimal estimatedCost;
}
