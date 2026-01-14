package com.elcafe.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRequest {

    @NotNull(message = "Ingredient ID is required")
    private Long ingredientId;

    private String batchNumber; // Auto-generated if not provided

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotNull(message = "Received date is required")
    private LocalDate receivedDate;

    private LocalDate expiryDate; // Auto-calculated from shelf life if not provided

    private BigDecimal costPerUnit;

    private Long supplierId;

    private String poReference;

    private String notes;
}
