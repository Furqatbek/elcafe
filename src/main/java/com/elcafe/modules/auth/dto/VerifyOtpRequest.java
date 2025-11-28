package com.elcafe.modules.auth.dto;

import com.elcafe.modules.customer.enums.RegistrationSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP code must be exactly 6 digits")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP code must be 6 digits")
    private String otpCode;

    // Customer registration data (for new users)
    private String firstName;

    private String lastName;

    private LocalDate birthDate;

    @NotNull(message = "Registration source is required")
    private RegistrationSource registrationSource;

    private String language; // e.g., "uz", "ru", "en"
}
