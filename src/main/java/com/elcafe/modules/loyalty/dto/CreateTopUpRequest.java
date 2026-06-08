package com.elcafe.modules.loyalty.dto;

import com.elcafe.modules.loyalty.entity.WalletTopUp;
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
public class CreateTopUpRequest {

    @NotNull
    @DecimalMin(value = "1000", inclusive = true,
            message = "Top-up amount must be at least 1000")
    private BigDecimal amount;

    @NotNull
    private WalletTopUp.Provider provider;
}
