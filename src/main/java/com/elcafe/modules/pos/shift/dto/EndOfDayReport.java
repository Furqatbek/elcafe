package com.elcafe.modules.pos.shift.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndOfDayReport {

    private LocalDate date;
    private int totalShifts;
    private BigDecimal totalSales;
    private BigDecimal totalCashSales;
    private BigDecimal totalCardSales;
    private BigDecimal totalTips;
    private BigDecimal totalRefunds;
    private BigDecimal totalVoids;
    private int totalOrders;
    private BigDecimal totalCashVariance;
    private List<ShiftSummaryDTO> shifts;
}
