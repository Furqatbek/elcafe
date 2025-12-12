package com.elcafe.modules.inventory.dto;

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
public class AdjustStockRequest {

    @NotNull(message = "New quantity is required")
    @PositiveOrZero(message = "New quantity must be zero or positive")
    private BigDecimal newQuantity;

    private String notes;

    @NotNull(message = "Performed by is required")
    private String performedBy;
}
