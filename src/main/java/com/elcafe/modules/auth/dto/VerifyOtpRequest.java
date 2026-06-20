package com.elcafe.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for OTP verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9+]{10,20}$", message = "Phone number must be 10-20 digits, may include +")
    private String phoneNumber;

    // V150: must match the restaurant the OTP was requested for so the right per-restaurant
    // customer is resolved and bound into the issued token.
    @NotNull(message = "Restaurant is required")
    private Long restaurantId;

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP code must be exactly 6 digits")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP code must be 6 digits")
    private String otpCode;
}
