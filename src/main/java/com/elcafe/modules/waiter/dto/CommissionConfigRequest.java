package com.elcafe.modules.waiter.dto;

import jakarta.validation.constraints.DecimalMax;
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
public class CommissionConfigRequest {

    @NotNull(message = "Commission percentage is required")
    @DecimalMin(value = "0.00", message = "Commission percentage must be at least 0")
    @DecimalMax(value = "100.00", message = "Commission percentage cannot exceed 100")
    private BigDecimal commissionPercent;

    @NotNull(message = "Commission enabled flag is required")
    private Boolean commissionEnabled;
}
