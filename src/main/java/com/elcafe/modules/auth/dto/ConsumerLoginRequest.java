package com.elcafe.modules.auth.dto;

import com.elcafe.modules.customer.enums.RegistrationSource;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty(value = "firstName", access = JsonProperty.Access.WRITE_ONLY)
    private String firstName;

    @JsonProperty(value = "name", access = JsonProperty.Access.WRITE_ONLY)
    private String name;

    @JsonProperty(value = "lastName", access = JsonProperty.Access.WRITE_ONLY)
    private String lastName;

    @JsonProperty(value = "lastname", access = JsonProperty.Access.WRITE_ONLY)
    private String lastname;

    private LocalDate birthDate;

    @NotNull(message = "Registration source is required")
    private RegistrationSource registrationSource;

    private String language; // e.g., "uz", "ru", "en"

    // Helper methods to get the correct values regardless of which field was used
    public String getFirstName() {
        if (firstName != null && !firstName.trim().isEmpty()) {
            return firstName;
        }
        return name;
    }

    public String getLastName() {
        if (lastName != null && !lastName.trim().isEmpty()) {
            return lastName;
        }
        return lastname;
    }
}
