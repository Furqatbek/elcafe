package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * Peak hours analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeakHoursDTO {

    private List<Integer> peakHours;

    private LocalTime averagePeakStart;

    private LocalTime averagePeakEnd;

    private Integer totalOrdersDuringPeakHours;

    private Integer totalOrdersOutsidePeakHours;

    private Double peakHoursPercentage;

    private List<SalesPerHourDTO> hourlySalesBreakdown;
}
