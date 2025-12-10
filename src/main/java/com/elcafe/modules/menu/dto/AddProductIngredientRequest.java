package com.elcafe.modules.menu.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddProductIngredientRequest {

    @NotNull(message = "Ingredient ID is required")
    private Long ingredientId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.001", message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotBlank(message = "Unit is required")
    @Size(max = 50, message = "Unit must not exceed 50 characters")
    private String unit;
}
