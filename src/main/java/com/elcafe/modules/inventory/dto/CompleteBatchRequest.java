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
public class CompleteBatchRequest {

    @NotNull(message = "Output quantity is required")
    @Positive(message = "Output quantity must be positive")
    private BigDecimal outputQuantity;

    private String outputUnit;

    private String notes;
}
