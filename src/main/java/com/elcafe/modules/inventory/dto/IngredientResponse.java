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
public class IngredientResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String name;
    private String description;
    private String unit;
    private BigDecimal currentStock;
    private BigDecimal minimumStock;
    private BigDecimal reorderLevel;
    private BigDecimal costPerUnit;
    private String supplier;
    private String sku;
    private Boolean active;
    private Boolean trackInventory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
