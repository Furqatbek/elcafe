package com.elcafe.modules.pos.tax.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaxExemptionTypeRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private String exemptionCode;

    @Builder.Default
    private Boolean requiresDocumentation = true;
}
