package com.elcafe.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for consumer profile update
 * Allows consumers to update their own profile information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConsumerProfileRequest {

    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    private String firstName;

    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    private String lastName;

    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @Pattern(regexp = "^[0-9+]{10,20}$", message = "Phone number must be 10-20 digits, may include +")
    private String phone;

    @JsonFormat(pattern = "dd.MM.yyyy")
    private LocalDate birthDate;

    @Pattern(regexp = "^(uz|ru|en|tr|ar)$", message = "Language must be one of: uz, ru, en, tr, ar")
    private String language;

    @Size(max = 500, message = "Default address must not exceed 500 characters")
    private String defaultAddress;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;

    @Size(max = 20, message = "Zip code must not exceed 20 characters")
    private String zipCode;
}
