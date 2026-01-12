package com.elcafe.modules.referral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyReferralRequest {

    @NotBlank(message = "Referral code is required")
    private String code;

    @NotNull(message = "Customer ID is required")
    private Long customerId;
}
