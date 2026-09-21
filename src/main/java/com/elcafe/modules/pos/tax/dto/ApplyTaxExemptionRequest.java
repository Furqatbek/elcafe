package com.elcafe.modules.pos.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyTaxExemptionRequest {

    private Long exemptionTypeId;

    private String exemptionNumber;

    private String reason;
}
