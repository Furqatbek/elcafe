package com.elcafe.modules.waiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterKPIConfigRequest {
    private Long id;
    private Long waiterId; // null for restaurant-wide default
    private String name;

    // Daily targets
    private Integer targetOrdersPerDay;
    private BigDecimal targetRevenuePerDay;
    private BigDecimal targetAvgTicket;
    private Integer targetTablesPerShift;

    // Service quality targets
    private Integer targetAvgServiceTimeMinutes;
    private BigDecimal maxComplaintRatePercent;
    private BigDecimal minCustomerRating;

    // Upselling targets
    private BigDecimal targetUpsellRatePercent;
    private BigDecimal targetDessertAttachRatePercent;
    private BigDecimal targetBeverageAttachRatePercent;

    // Bonus configuration
    private BigDecimal bonusThresholdPercent;
    private BigDecimal bonusAmountPerThreshold;

    private Boolean active;
}
