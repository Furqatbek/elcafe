package com.elcafe.modules.customer.dto;

import com.elcafe.modules.customer.enums.RegistrationSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCustomerRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;

    private String defaultAddress;
    private String city;
    private String state;
    private String zipCode;
    private String notes;
    private String tags;
    private LocalDate birthDate;
    private String language;
    private RegistrationSource registrationSource;
    private Boolean active;

    // Referral code for the referral program
    private String referralCode;

    // Restaurant ID for multi-tenant support
    private Long restaurantId;
}
