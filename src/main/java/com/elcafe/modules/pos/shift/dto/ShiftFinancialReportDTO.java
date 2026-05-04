package com.elcafe.modules.pos.shift.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftFinancialReportDTO {

    private LocalDate date;
    private Long restaurantId;
    private BigDecimal totalRevenue;
    private int totalOrders;
    private int totalShifts;
    private BigDecimal totalLaborHours;
    private BigDecimal laborCostEstimate;
    private BigDecimal laborCostRatio;

    private List<ShiftRevenueEntry> shifts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShiftRevenueEntry {
        private Long shiftId;
        private Long employeeId;
        private String employeeName;
        private OffsetDateTime clockIn;
        private OffsetDateTime clockOut;
        private long workedMinutes;
        private int breakMinutes;
        private BigDecimal revenue;
        private int orderCount;
        private BigDecimal laborCost;
        private BigDecimal laborCostRatio;
    }
}
