package com.elcafe.modules.financial.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.entity.SalaryConfig;
import com.elcafe.modules.financial.entity.SalaryConfig.PayFrequency;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.financial.repository.SalaryConfigRepository;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalaryAutoPayServiceTest {

    private static final Long RESTAURANT_ID = 7L;
    private static final Long EMPLOYEE_ID = 42L;

    @Mock private SalaryConfigRepository salaryConfigRepository;
    @Mock private PayrollEntryRepository payrollEntryRepository;
    @Mock private PayrollService payrollService;
    @Mock private EmployeeShiftRepository shiftRepository;

    @InjectMocks
    private SalaryAutoPayService service;

    private Restaurant restaurant;
    private User employee;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);

        employee = new User();
        employee.setId(EMPLOYEE_ID);
        employee.setEmail("alice@example.com");

        // Payroll wiring — return whatever was passed in, with an id, so the
        // service's downstream calls (approve, processPayment) work.
        when(payrollService.createPayrollEntry(any(PayrollEntry.class)))
                .thenAnswer(inv -> {
                    PayrollEntry e = inv.getArgument(0);
                    e.setId(100L);
                    return e;
                });
    }

    private SalaryConfig configWith(PayFrequency freq, BigDecimal amount,
                                     Integer payDay, Integer payDayOfWeek) {
        return SalaryConfig.builder()
                .id(1L)
                .restaurant(restaurant)
                .employee(employee)
                .payFrequency(freq)
                .baseAmount(amount)
                .payDay(payDay)
                .payDayOfWeek(payDayOfWeek)
                .autoApprove(true)
                .active(true)
                .build();
    }

    private EmployeeShift shift(LocalDate date, boolean paidForSalary) {
        return EmployeeShift.builder()
                .id(date.toEpochDay())
                .restaurant(restaurant)
                .employee(employee)
                .shiftDate(date)
                .clockOut(OffsetDateTime.now())
                .paidForSalary(paidForSalary)
                .build();
    }

    /* ------------------------------------------------------------------ */
    /* MONTHLY                                                            */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("MONTHLY")
    class MonthlyTests {

        @Test
        @DisplayName("pays the configured base amount once on the configured day")
        void paysOnPayDay() {
            SalaryConfig cfg = configWith(PayFrequency.MONTHLY, new BigDecimal("5000000"), 15, null);
            LocalDate payday = LocalDate.of(2026, 3, 15);
            when(salaryConfigRepository.findActiveNotYetPaidToday(payday)).thenReturn(List.of(cfg));
            when(payrollEntryRepository.findByRestaurant_IdAndPayPeriodStartBetween(anyLong(), any(), any()))
                    .thenReturn(List.of());

            service.processPayment(cfg, payday);

            ArgumentCaptor<PayrollEntry> captor = ArgumentCaptor.forClass(PayrollEntry.class);
            verify(payrollService).createPayrollEntry(captor.capture());
            PayrollEntry e = captor.getValue();
            assertThat(e.getBaseSalary()).isEqualByComparingTo("5000000");
            assertThat(e.getPayPeriodStart()).isEqualTo(LocalDate.of(2026, 2, 15));
            assertThat(e.getPayPeriodEnd()).isEqualTo(LocalDate.of(2026, 3, 14));
            assertThat(cfg.getLastPaidDate()).isEqualTo(LocalDate.of(2026, 3, 14));
        }

        @Test
        @DisplayName("refuses to pay twice in the same monthly period")
        void doublePaymentBlocked() {
            SalaryConfig cfg = configWith(PayFrequency.MONTHLY, new BigDecimal("5000000"), 15, null);
            cfg.setLastPaidDate(LocalDate.of(2026, 3, 14));

            assertThatThrownBy(() -> service.processPayment(cfg, LocalDate.of(2026, 3, 15)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already paid");
            verify(payrollService, never()).createPayrollEntry(any());
        }
    }

    /* ------------------------------------------------------------------ */
    /* DAILY                                                              */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("DAILY")
    class DailyTests {

        @Test
        @DisplayName("pays the base amount once if the employee worked a shift yesterday")
        void paysWhenShiftExists() {
            SalaryConfig cfg = configWith(PayFrequency.DAILY, new BigDecimal("80000"), null, null);
            LocalDate today = LocalDate.of(2026, 4, 10);
            LocalDate yesterday = today.minusDays(1);

            when(shiftRepository.findByRestaurantAndDateRange(RESTAURANT_ID, yesterday, yesterday))
                    .thenReturn(List.of(shift(yesterday, false)));
            when(payrollEntryRepository.findByRestaurant_IdAndPayPeriodStartBetween(anyLong(), any(), any()))
                    .thenReturn(List.of());

            service.processPayment(cfg, today);

            ArgumentCaptor<PayrollEntry> captor = ArgumentCaptor.forClass(PayrollEntry.class);
            verify(payrollService).createPayrollEntry(captor.capture());
            assertThat(captor.getValue().getBaseSalary()).isEqualByComparingTo("80000");
            assertThat(captor.getValue().getPayPeriodStart()).isEqualTo(yesterday);
            assertThat(captor.getValue().getPayPeriodEnd()).isEqualTo(yesterday);
        }

        @Test
        @DisplayName("skips payroll creation but still advances the cursor when no shift was worked")
        void skipsWhenNoShift() {
            SalaryConfig cfg = configWith(PayFrequency.DAILY, new BigDecimal("80000"), null, null);
            LocalDate today = LocalDate.of(2026, 4, 10);
            LocalDate yesterday = today.minusDays(1);

            when(shiftRepository.findByRestaurantAndDateRange(RESTAURANT_ID, yesterday, yesterday))
                    .thenReturn(List.of());

            service.processPayment(cfg, today);

            verify(payrollService, never()).createPayrollEntry(any());
            assertThat(cfg.getLastPaidDate()).isEqualTo(yesterday);
        }

        @Test
        @DisplayName("ignores shifts already settled by the fire-on-clock-out hook")
        void ignoresAlreadyPaidShifts() {
            SalaryConfig cfg = configWith(PayFrequency.DAILY, new BigDecimal("80000"), null, null);
            LocalDate today = LocalDate.of(2026, 4, 10);
            LocalDate yesterday = today.minusDays(1);

            when(shiftRepository.findByRestaurantAndDateRange(RESTAURANT_ID, yesterday, yesterday))
                    .thenReturn(List.of(shift(yesterday, true)));

            service.processPayment(cfg, today);

            verify(payrollService, never()).createPayrollEntry(any());
        }
    }

    /* ------------------------------------------------------------------ */
    /* PER_SHIFT                                                          */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("PER_SHIFT")
    class PerShiftTests {

        @Test
        @DisplayName("batcher: pays baseAmount × shiftsYesterday and flips the paid flag on each shift")
        void batcherPaysCountAndMarksShifts() {
            SalaryConfig cfg = configWith(PayFrequency.PER_SHIFT, new BigDecimal("60000"), null, null);
            LocalDate today = LocalDate.of(2026, 4, 10);
            LocalDate yesterday = today.minusDays(1);
            EmployeeShift s1 = shift(yesterday, false);
            EmployeeShift s2 = shift(yesterday, false);
            s2.setId(s1.getId() + 1);
            when(shiftRepository.findByRestaurantAndDateRange(RESTAURANT_ID, yesterday, yesterday))
                    .thenReturn(List.of(s1, s2));
            when(payrollEntryRepository.findByRestaurant_IdAndPayPeriodStartBetween(anyLong(), any(), any()))
                    .thenReturn(List.of());

            service.processPayment(cfg, today);

            ArgumentCaptor<PayrollEntry> captor = ArgumentCaptor.forClass(PayrollEntry.class);
            verify(payrollService).createPayrollEntry(captor.capture());
            // 60000 × 2 shifts
            assertThat(captor.getValue().getBaseSalary()).isEqualByComparingTo("120000");
            assertThat(s1.getPaidForSalary()).isTrue();
            assertThat(s2.getPaidForSalary()).isTrue();
            verify(shiftRepository).save(s1);
            verify(shiftRepository).save(s2);
        }

        @Test
        @DisplayName("clock-out hook: pays exactly baseAmount, marks shift paid, advances cursor")
        void clockOutHookPaysOneShift() {
            SalaryConfig cfg = configWith(PayFrequency.PER_SHIFT, new BigDecimal("60000"), null, null);
            LocalDate shiftDay = LocalDate.of(2026, 4, 12);
            EmployeeShift s = shift(shiftDay, false);

            when(salaryConfigRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                    .thenReturn(List.of(cfg));
            when(payrollEntryRepository.findByRestaurant_IdAndPayPeriodStartBetween(anyLong(), any(), any()))
                    .thenReturn(List.of());

            service.processClockOut(s);

            ArgumentCaptor<PayrollEntry> captor = ArgumentCaptor.forClass(PayrollEntry.class);
            verify(payrollService).createPayrollEntry(captor.capture());
            PayrollEntry e = captor.getValue();
            assertThat(e.getBaseSalary()).isEqualByComparingTo("60000");
            assertThat(e.getPayPeriodStart()).isEqualTo(shiftDay);
            assertThat(e.getPayPeriodEnd()).isEqualTo(shiftDay);
            assertThat(e.getNotes()).contains("PER_SHIFT");
            assertThat(s.getPaidForSalary()).isTrue();
            assertThat(cfg.getLastPaidDate()).isEqualTo(shiftDay);
        }

        @Test
        @DisplayName("clock-out hook: ignores shifts already marked paid for salary")
        void clockOutHookSkipsAlreadyPaid() {
            EmployeeShift s = shift(LocalDate.of(2026, 4, 12), true);
            service.processClockOut(s);
            verify(payrollService, never()).createPayrollEntry(any());
        }

        @Test
        @DisplayName("clock-out hook: does nothing for MONTHLY-only configs")
        void clockOutHookIgnoresMonthlyConfigs() {
            SalaryConfig cfg = configWith(PayFrequency.MONTHLY, new BigDecimal("5000000"), 15, null);
            when(salaryConfigRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                    .thenReturn(List.of(cfg));

            service.processClockOut(shift(LocalDate.of(2026, 4, 12), false));

            verify(payrollService, never()).createPayrollEntry(any());
        }
    }

    /* ------------------------------------------------------------------ */
    /* WEEKLY                                                             */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("WEEKLY")
    class WeeklyTests {

        @Test
        @DisplayName("pays baseAmount for the previous 7 days")
        void paysWeeklyAmount() {
            // 2026-04-13 is a Monday — dayOfWeek = 1
            SalaryConfig cfg = configWith(PayFrequency.WEEKLY, new BigDecimal("1200000"), null, 1);
            LocalDate payday = LocalDate.of(2026, 4, 13);
            when(payrollEntryRepository.findByRestaurant_IdAndPayPeriodStartBetween(anyLong(), any(), any()))
                    .thenReturn(List.of());

            service.processPayment(cfg, payday);

            ArgumentCaptor<PayrollEntry> captor = ArgumentCaptor.forClass(PayrollEntry.class);
            verify(payrollService).createPayrollEntry(captor.capture());
            assertThat(captor.getValue().getBaseSalary()).isEqualByComparingTo("1200000");
            assertThat(captor.getValue().getPayPeriodStart()).isEqualTo(payday.minusWeeks(1));
            assertThat(captor.getValue().getPayPeriodEnd()).isEqualTo(payday.minusDays(1));
        }
    }

    /* ------------------------------------------------------------------ */
    /* HOURLY (stub — must not auto-pay yet)                              */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("HOURLY")
    class HourlyTests {

        @Test
        @DisplayName("manual processPayment is a no-op until clocked-hour aggregation is wired up")
        void hourlyDoesNotAutoPay() {
            SalaryConfig cfg = configWith(PayFrequency.HOURLY, new BigDecimal("30000"), null, null);

            service.processPayment(cfg, LocalDate.of(2026, 4, 10));

            // computeBaseAmount returns ZERO for HOURLY → service short-circuits,
            // advances cursor, no payroll entry written.
            verify(payrollService, never()).createPayrollEntry(any());
            assertThat(cfg.getLastPaidDate()).isEqualTo(LocalDate.of(2026, 4, 9));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Avoidance: dummy assertion to keep Mockito's Optional import alive */
    /* ------------------------------------------------------------------ */
    @SuppressWarnings("unused")
    private void keepOptionalImport() {
        Optional.empty();
    }
}
