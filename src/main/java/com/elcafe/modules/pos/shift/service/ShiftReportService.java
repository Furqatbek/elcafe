package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.pos.shift.dto.ShiftFinancialReportDTO;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftReportService {

    private final EmployeeShiftRepository shiftRepository;

    /**
     * Generate shift-based financial report for a restaurant on a given date.
     * Shows revenue, orders, labor hours, and labor cost ratio per shift.
     *
     * @param hourlyRate average hourly labor cost (for labor cost estimate)
     */
    @Transactional(readOnly = true)
    public ShiftFinancialReportDTO getShiftReport(Long restaurantId, LocalDate date, BigDecimal hourlyRate) {
        List<EmployeeShift> shifts = shiftRepository.findByRestaurantIdAndShiftDate(restaurantId, date);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        int totalOrders = 0;
        BigDecimal totalLaborMinutes = BigDecimal.ZERO;

        List<ShiftFinancialReportDTO.ShiftRevenueEntry> entries = shifts.stream()
                .map(shift -> {
                    BigDecimal revenue = shift.getTotalSales() != null ? shift.getTotalSales() : BigDecimal.ZERO;
                    int orders = shift.getTotalOrders() != null ? shift.getTotalOrders() : 0;
                    long workedMin = shift.getWorkedMinutes();
                    int breakMin = shift.getBreakMinutes() != null ? shift.getBreakMinutes() : 0;
                    // getWorkedMinutes() already excludes break time; do not subtract breakMin again
                    // (that double-counted the break and understated paid labour hours).
                    long netMinutes = Math.max(0, workedMin);

                    BigDecimal laborCost = hourlyRate != null
                            ? hourlyRate.multiply(BigDecimal.valueOf(netMinutes)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    BigDecimal ratio = revenue.compareTo(BigDecimal.ZERO) > 0
                            ? laborCost.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    return ShiftFinancialReportDTO.ShiftRevenueEntry.builder()
                            .shiftId(shift.getId())
                            .employeeId(shift.getEmployee() != null ? shift.getEmployee().getId() : (shift.getWaiter() != null ? shift.getWaiter().getId() : null))
                            .employeeName(shift.getEmployee() != null ? shift.getEmployee().getFullName() : (shift.getWaiter() != null ? shift.getWaiter().getName() : "Unknown"))
                            .clockIn(shift.getClockIn())
                            .clockOut(shift.getClockOut())
                            .workedMinutes(workedMin)
                            .breakMinutes(breakMin)
                            .revenue(revenue)
                            .orderCount(orders)
                            .laborCost(laborCost)
                            .laborCostRatio(ratio)
                            .build();
                })
                .toList();

        for (var e : entries) {
            totalRevenue = totalRevenue.add(e.getRevenue());
            totalOrders += e.getOrderCount();
            totalLaborMinutes = totalLaborMinutes.add(BigDecimal.valueOf(e.getWorkedMinutes()));
        }

        BigDecimal totalLaborHours = totalLaborMinutes.divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
        BigDecimal totalLaborCost = hourlyRate != null
                ? hourlyRate.multiply(totalLaborMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal overallRatio = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? totalLaborCost.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return ShiftFinancialReportDTO.builder()
                .date(date)
                .restaurantId(restaurantId)
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .totalShifts(shifts.size())
                .totalLaborHours(totalLaborHours)
                .laborCostEstimate(totalLaborCost)
                .laborCostRatio(overallRatio)
                .shifts(entries)
                .build();
    }
}
