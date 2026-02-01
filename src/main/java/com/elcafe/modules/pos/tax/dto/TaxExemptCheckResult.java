package com.elcafe.modules.pos.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxExemptCheckResult {

    private Long customerId;
    private boolean isTaxExempt;
    private String exemptionNumber;
    private String exemptionTypeName;
    private LocalDate expiresAt;
    private boolean isExpired;
}
