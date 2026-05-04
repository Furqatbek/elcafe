package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.pos.shift.dto.ShiftFinancialReportDTO;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.enums.ShiftStatus;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftReportServiceTest {

    @Mock private EmployeeShiftRepository shiftRepository;
    @InjectMocks private ShiftReportService service;

    private Restaurant restaurant;
    private User ali;
    private User jasur;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);

        ali = new User();
        ali.setId(10L);
        ali.setFirstName("Ali");
        ali.setLastName("K");

        jasur = new User();
        jasur.setId(11L);
        jasur.setFirstName("Jasur");
        jasur.setLastName("M");
    }

    private EmployeeShift shift(User emp, int startHour, int endHour, BigDecimal sales, int orders) {
        EmployeeShift s = new EmployeeShift();
        s.setId((long) (Math.random() * 10000));
        s.setRestaurant(restaurant);
        s.setEmployee(emp);
        s.setShiftDate(LocalDate.of(2026, 5, 1));
        s.setClockIn(OffsetDateTime.of(2026, 5, 1, startHour, 0, 0, 0, ZoneOffset.UTC));
        s.setClockOut(OffsetDateTime.of(2026, 5, 1, endHour, 0, 0, 0, ZoneOffset.UTC));
        s.setStatus(ShiftStatus.COMPLETED);
        s.setTotalSales(sales);
        s.setTotalOrders(orders);
        s.setBreakMinutes(30);
        return s;
    }

    @Test @DisplayName("revenue grouped by shift returns correct totals")
    void revenueByShift() {
        when(shiftRepository.findByRestaurantIdAndShiftDate(1L, LocalDate.of(2026, 5, 1)))
                .thenReturn(List.of(
                        shift(ali, 8, 16, new BigDecimal("3500000"), 42),
                        shift(jasur, 12, 20, new BigDecimal("2800000"), 31)
                ));

        ShiftFinancialReportDTO report = service.getShiftReport(1L, LocalDate.of(2026, 5, 1), new BigDecimal("25000"));

        assertThat(report.getTotalRevenue()).isEqualByComparingTo("6300000");
        assertThat(report.getTotalOrders()).isEqualTo(73);
        assertThat(report.getShifts()).hasSize(2);
        assertThat(report.getShifts().get(0).getRevenue()).isEqualByComparingTo("3500000");
    }

    @Test @DisplayName("labor cost ratio calculation")
    void laborCostRatio() {
        when(shiftRepository.findByRestaurantIdAndShiftDate(1L, LocalDate.of(2026, 5, 1)))
                .thenReturn(List.of(
                        shift(ali, 8, 16, new BigDecimal("1000000"), 20)
                ));

        ShiftFinancialReportDTO report = service.getShiftReport(1L, LocalDate.of(2026, 5, 1), new BigDecimal("25000"));

        // Ali: 8h shift - 30min break = 7.5h net
        // Labor cost: 25000 * 7.5 = 187500
        // Ratio: 187500 / 1000000 * 100 = 18.75%
        assertThat(report.getShifts().get(0).getLaborCost()).isEqualByComparingTo("187500");
        assertThat(report.getLaborCostRatio().doubleValue()).isBetween(18.0, 19.0);
    }

    @Test @DisplayName("per-employee order count and revenue")
    void perEmployeeDetails() {
        when(shiftRepository.findByRestaurantIdAndShiftDate(1L, LocalDate.of(2026, 5, 1)))
                .thenReturn(List.of(
                        shift(ali, 8, 16, new BigDecimal("1200000"), 42),
                        shift(jasur, 9, 17, new BigDecimal("890000"), 31)
                ));

        ShiftFinancialReportDTO report = service.getShiftReport(1L, LocalDate.of(2026, 5, 1), new BigDecimal("25000"));

        var aliEntry = report.getShifts().stream().filter(e -> e.getEmployeeId() == 10L).findFirst().orElseThrow();
        var jasurEntry = report.getShifts().stream().filter(e -> e.getEmployeeId() == 11L).findFirst().orElseThrow();

        assertThat(aliEntry.getOrderCount()).isEqualTo(42);
        assertThat(aliEntry.getRevenue()).isEqualByComparingTo("1200000");
        assertThat(jasurEntry.getOrderCount()).isEqualTo(31);
        assertThat(jasurEntry.getRevenue()).isEqualByComparingTo("890000");
    }
}
