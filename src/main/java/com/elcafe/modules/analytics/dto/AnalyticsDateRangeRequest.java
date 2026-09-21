package com.elcafe.modules.analytics.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Common request DTO for analytics with date range filtering
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDateRangeRequest {

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private Long restaurantId;

    private Long branchId;
}
