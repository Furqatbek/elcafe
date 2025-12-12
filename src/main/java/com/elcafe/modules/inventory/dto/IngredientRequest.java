package com.elcafe.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotBlank(message = "Unit is required")
    private String unit;

    @NotNull(message = "Current stock is required")
    @PositiveOrZero(message = "Current stock must be zero or positive")
    private BigDecimal currentStock;

    @NotNull(message = "Minimum stock is required")
    @PositiveOrZero(message = "Minimum stock must be zero or positive")
    private BigDecimal minimumStock;

    @NotNull(message = "Reorder level is required")
    @PositiveOrZero(message = "Reorder level must be zero or positive")
    private BigDecimal reorderLevel;

    @PositiveOrZero(message = "Cost per unit must be zero or positive")
    private BigDecimal costPerUnit;

    private String supplier;

    private String sku;

    private Boolean active;

    private Boolean trackInventory;
}
