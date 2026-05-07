package com.elcafe.modules.pos.shift.dto;

import com.elcafe.modules.pos.shift.enums.ShiftStatus;
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
public class ShiftSummaryDTO {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private LocalDate shiftDate;
    private OffsetDateTime clockIn;
    private OffsetDateTime clockOut;
    private ShiftStatus status;
    private long workedMinutes;
    private Integer breakMinutes;
    private BigDecimal totalSales;
    private BigDecimal totalCashSales;
    private BigDecimal totalCardSales;
    private Integer totalOrders;
    private BigDecimal cashVariance;
}
