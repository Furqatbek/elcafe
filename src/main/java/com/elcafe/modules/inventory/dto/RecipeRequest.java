package com.elcafe.modules.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Ingredient ID is required")
    private Long ingredientId;

    @NotNull(message = "Quantity required is required")
    @Positive(message = "Quantity required must be positive")
    private BigDecimal quantityRequired;

    private String unit;

    private String notes;

    private Boolean optional;
}
