package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Table turnover rate analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableTurnoverDTO {

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer totalTables;

    private Integer totalSeats;

    private Integer totalDineInOrders;

    private Double averageTurnoverRate; // orders per table per day

    private Double averageOccupancyRate; // percentage

    private Integer totalOperatingHours;

    private Double ordersPerSeatPerDay;
}
