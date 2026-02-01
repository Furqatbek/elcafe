package com.elcafe.modules.pos.giftcard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGiftCardTypeRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private List<BigDecimal> fixedAmounts;

    private BigDecimal minAmount;

    private BigDecimal maxAmount;

    @Builder.Default
    private Boolean isCustomAmountAllowed = true;

    @Builder.Default
    private Integer validityDays = 365;

    @Builder.Default
    private Boolean isRechargeable = true;
}
