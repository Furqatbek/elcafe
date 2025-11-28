package com.elcafe.modules.auth.dto;

import com.elcafe.modules.customer.enums.RegistrationSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for consumer login (OTP request)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerLoginRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9+]{10,20}$", message = "Phone number must be 10-20 digits, may include +")
    private String phoneNumber;

    private String firstName;

    private String lastName;

    private LocalDate birthDate;

    @NotNull(message = "Registration source is required")
    private RegistrationSource registrationSource;

    private String language; // e.g., "uz", "ru", "en"
}
