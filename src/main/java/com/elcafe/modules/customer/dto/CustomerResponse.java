package com.elcafe.modules.customer.dto;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.enums.RegistrationSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    // Basic customer info
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String qrCode;
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
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Marketing fields - Loyalty
    private BigDecimal bonusBalance;
    private BigDecimal lifetimeEarned;
    private BigDecimal lifetimeSpent;
    private BigDecimal totalSpent;
    private Integer orderCount;
    private String tierName;
    private OffsetDateTime lastOrderDate;

    // Marketing fields - Referral
    private String referralCode;
    private Integer referralUsageCount;
    private Integer referralSuccessCount;

    /**
     * Create from Customer entity (basic fields only)
     */
    public static CustomerResponse from(Customer customer) {
        if (customer == null) return null;

        return CustomerResponse.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .qrCode(customer.getQrCode())
                .defaultAddress(customer.getDefaultAddress())
                .city(customer.getCity())
                .state(customer.getState())
                .zipCode(customer.getZipCode())
                .notes(customer.getNotes())
                .tags(customer.getTags())
                .birthDate(customer.getBirthDate())
                .language(customer.getLanguage())
                .registrationSource(customer.getRegistrationSource())
                .active(customer.getActive())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}
