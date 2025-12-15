package com.elcafe.modules.menu.dto;

import com.elcafe.modules.menu.enums.IngredientCategory;
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
public class CreateIngredientRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotBlank(message = "Unit is required")
    @Size(max = 50, message = "Unit must not exceed 50 characters")
    private String unit;

    @NotNull(message = "Cost per unit is required")
    @DecimalMin(value = "0.0", message = "Cost per unit must be positive")
    private BigDecimal costPerUnit;

    @DecimalMin(value = "0.0", message = "Current stock must be positive")
    private BigDecimal currentStock;

    @DecimalMin(value = "0.0", message = "Minimum stock must be positive")
    private BigDecimal minimumStock;

    @Size(max = 200, message = "Supplier must not exceed 200 characters")
    private String supplier;

    private IngredientCategory category;
}
