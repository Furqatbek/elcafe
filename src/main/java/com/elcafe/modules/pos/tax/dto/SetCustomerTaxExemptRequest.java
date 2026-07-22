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
public class SetCustomerTaxExemptRequest {

    private Long exemptionTypeId;

    private String exemptionNumber;

    private LocalDate expiresAt;
}
