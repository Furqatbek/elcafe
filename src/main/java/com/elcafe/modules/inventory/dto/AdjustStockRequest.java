package com.elcafe.modules.inventory.dto;

import jakarta.validation.constraints.DecimalMax;
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
    @DecimalMax(value = "999999.99", message = "New quantity must not exceed 999999.99")
    private BigDecimal newQuantity;

    private String notes;

    /**
     * @deprecated This field is ignored for security reasons.
     * The authenticated user's identity is used instead to prevent audit trail falsification.
     * This field is retained for backward compatibility with existing API clients.
     */
    @Deprecated
    private String performedBy;
}
