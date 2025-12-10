package com.elcafe.modules.customer.dto;

import com.elcafe.modules.customer.enums.RegistrationSource;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for consumer profile
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerProfileResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;

    @JsonFormat(pattern = "dd.MM.yyyy")
    private LocalDate birthDate;

    private String language;
    private RegistrationSource registrationSource;
    private String defaultAddress;
    private String city;
    private String state;
    private String zipCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
