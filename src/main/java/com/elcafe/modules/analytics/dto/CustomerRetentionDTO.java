package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Customer retention rate analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRetentionDTO {

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer customersAtStart;

    private Integer newCustomers;

    private Integer customersAtEnd;

    private Integer returningCustomers;

    private Double retentionRate; // percentage

    private Double churnRate; // percentage

    private Integer oneTimeCustomers;

    private Integer repeatCustomers;

    private Double repeatCustomerRate; // percentage
}
