package com.elcafe.modules.customer.dto;

import com.elcafe.modules.customer.enums.RegistrationSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerActivityFilterDTO {
    // Period filter
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    // RFM filters
    private Integer minRecency; // Minimum days since last order
    private Integer maxRecency; // Maximum days since last order
    private Integer minFrequency; // Minimum number of orders
    private Integer maxFrequency; // Maximum number of orders
    private BigDecimal minMonetary; // Minimum total spent
    private BigDecimal maxMonetary; // Maximum total spent

    // Registration source filter
    private RegistrationSource registrationSource;

    // Active status filter
    private Boolean active;

    // Search filter
    private String searchTerm;
}
