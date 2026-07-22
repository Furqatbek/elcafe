package com.elcafe.modules.loyalty.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UseBonusRequest {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Order amount is required")
    @DecimalMin(value = "0.01", message = "Order amount must be positive")
    private BigDecimal orderAmount;

    @NotNull(message = "Bonus amount is required")
    @DecimalMin(value = "0.01", message = "Bonus amount must be positive")
    private BigDecimal bonusAmount;
}
