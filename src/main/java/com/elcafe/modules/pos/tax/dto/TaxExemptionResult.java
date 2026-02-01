package com.elcafe.modules.pos.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxExemptionResult {

    private Long orderId;
    private BigDecimal taxExempted;
    private BigDecimal newTotal;
    private String exemptionNumber;
}
