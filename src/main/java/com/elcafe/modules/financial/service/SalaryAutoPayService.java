package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.entity.SalaryConfig;
import com.elcafe.modules.financial.entity.SalaryConfig.PayFrequency;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.financial.repository.SalaryConfigRepository;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryAutoPayService {

    private final SalaryConfigRepository salaryConfigRepository;
    private final PayrollEntryRepository payrollEntryRepository;
    private final PayrollService payrollService;
    private final EmployeeShiftRepository shiftRepository;

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void processScheduledSalaries() {
        LocalDate today = LocalDate.now();
        List<SalaryConfig> candidates = salaryConfigRepository.findActiveNotYetPaidToday(today);
        if (candidates.isEmpty()) {
            log.debug("No salary configs candidates today");
            return;
        }

        int processed = 0;
        for (SalaryConfig config : candidates) {
            if (!isDueToday(config, today)) continue;
            try {
                processPayment(config, today);
                processed++;
            } catch (Exception e) {
                log.error("Salary payment failed for config {} ({}): {}",
                        config.getId(), config.getPayFrequency(), e.getMessage());
            }
        }
        log.info("Auto-pay run on {}: {}/{} configs paid", today, processed, candidates.size());
    }

    /**
     * Manual "pay now" trigger from the admin UI. Bypasses the
     * isDueToday check (operators may want to pay early) but still
     * applies the same per-frequency period and double-payment guards.
     */
    @Transactional
    public void processPayment(SalaryConfig config, LocalDate paymentDate) {
        PayFrequency freq = config.getPayFrequency() != null
                ? config.getPayFrequency()
                : PayFrequency.MONTHLY;
        Period period = computePeriod(freq, config, paymentDate);

        if (alreadyPaidForPeriod(config, period)) {
            throw new IllegalStateException(
                    "Salary already paid for this period (last paid: " + config.getLastPaidDate() + ")");
        }

        BigDecimal baseAmount = computeBaseAmount(freq, config, period);
        if (baseAmount.compareTo(BigDecimal.ZERO) <= 0 && freq != PayFrequency.MONTHLY) {
            log.info("Skipping {} payment for config {} — nothing to pay ({} earned in period)",
                    freq, config.getId(), baseAmount);
            // Still mark as "paid" for this period so we don't keep re-evaluating;
            // alternative is to leave lastPaidDate alone and re-try tomorrow. The
            // safer choice is to advance the cursor so the operator doesn't see
            // ghost rows piling up.
            config.setLastPaidDate(period.end);
            salaryConfigRepository.save(config);
            return;
        }

        Long empId = config.getEmployee() != null ? config.getEmployee().getId() : null;
        BigDecimal advanceDeduction = empId != null
                ? calculateUnpaidAdvances(config.getRestaurant().getId(), empId, period.start, period.end)
                : BigDecimal.ZERO;

        PayrollEntry entry = PayrollEntry.builder()
                .restaurant(config.getRestaurant())
                .employee(config.getEmployee())
                .waiter(config.getWaiter())
                .payrollType(PayrollEntry.PayrollType.SALARY)
                .payPeriodStart(period.start)
                .payPeriodEnd(period.end)
                .baseSalary(baseAmount)
                .otherDeductions(advanceDeduction)
                .notes(buildNote(freq, period, advanceDeduction))
                .build();

        PayrollEntry saved = payrollService.createPayrollEntry(entry);
        if (Boolean.TRUE.equals(config.getAutoApprove())) {
            payrollService.approvePayrollEntry(saved.getId(), "System");
            payrollService.processPayment(saved.getId(), paymentDate, config.getPaymentMethod(), null);
        }

        config.setLastPaidDate(period.end);
        salaryConfigRepository.save(config);

        log.info("Salary paid for config {} ({}): {} for {} → {} (advances {})",
                config.getId(), freq, baseAmount, period.start, period.end, advanceDeduction);
    }

    /* ---------------------------------------------------------------------- */
    /* Frequency dispatch                                                     */
    /* ---------------------------------------------------------------------- */

    private boolean isDueToday(SalaryConfig config, LocalDate today) {
        PayFrequency freq = config.getPayFrequency() != null ? config.getPayFrequency() : PayFrequency.MONTHLY;
        switch (freq) {
            case MONTHLY: {
                Integer payDay = config.getPayDay();
                return payDay != null && today.getDayOfMonth() == clampDayOfMonth(payDay, today);
            }
            case WEEKLY:
            case BIWEEKLY: {
                Integer dow = config.getPayDayOfWeek();
                if (dow == null) return false;
                if (today.getDayOfWeek().getValue() != dow) return false;
                if (freq == PayFrequency.WEEKLY) return true;
                // BIWEEKLY: only pay if it has been ≥ 14 days since the last
                // payment (or never paid).
                LocalDate last = config.getLastPaidDate();
                return last == null || ChronoUnit.DAYS.between(last, today) >= 14;
            }
            case DAILY:
            case PER_SHIFT:
                // Settle yesterday's work today; only pay if we haven't
                // already paid for yesterday.
                LocalDate yesterday = today.minusDays(1);
                LocalDate last = config.getLastPaidDate();
                return last == null || last.isBefore(yesterday);
            case HOURLY:
                // Not yet auto-paid by the scheduler; admins can still
                // create payroll entries manually via the UI.
                return false;
            default:
                return false;
        }
    }

    private Period computePeriod(PayFrequency freq, SalaryConfig config, LocalDate paymentDate) {
        switch (freq) {
            case MONTHLY: {
                Integer payDay = config.getPayDay() != null ? config.getPayDay() : paymentDate.getDayOfMonth();
                LocalDate previousPayDay = paymentDate.minusMonths(1)
                        .withDayOfMonth(clampDayOfMonth(payDay, paymentDate.minusMonths(1)));
                return new Period(previousPayDay, paymentDate.minusDays(1));
            }
            case WEEKLY:
                return new Period(paymentDate.minusWeeks(1), paymentDate.minusDays(1));
            case BIWEEKLY:
                return new Period(paymentDate.minusWeeks(2), paymentDate.minusDays(1));
            case DAILY:
            case PER_SHIFT: {
                LocalDate target = paymentDate.minusDays(1);
                return new Period(target, target);
            }
            case HOURLY: {
                LocalDate target = paymentDate.minusDays(1);
                return new Period(target, target);
            }
            default:
                return new Period(paymentDate, paymentDate);
        }
    }

    private BigDecimal computeBaseAmount(PayFrequency freq, SalaryConfig config, Period period) {
        BigDecimal base = config.effectiveBaseAmount();
        switch (freq) {
            case MONTHLY:
            case WEEKLY:
            case BIWEEKLY:
                return base;
            case DAILY: {
                // Pay only if the employee/waiter actually worked a shift
                // in the period. Multiple shifts in the same day still
                // count as one daily wage.
                long shiftsInPeriod = countShiftsInPeriod(config, period);
                return shiftsInPeriod > 0 ? base : BigDecimal.ZERO;
            }
            case PER_SHIFT: {
                long shiftsInPeriod = countShiftsInPeriod(config, period);
                return base.multiply(BigDecimal.valueOf(shiftsInPeriod));
            }
            case HOURLY:
                // Stubbed: HOURLY isn't auto-paid yet. Returning zero
                // here keeps the manual "pay now" path safe.
                return BigDecimal.ZERO;
            default:
                return base;
        }
    }

    private long countShiftsInPeriod(SalaryConfig config, Period period) {
        List<EmployeeShift> all = shiftRepository.findByRestaurantAndDateRange(
                config.getRestaurant().getId(), period.start, period.end);
        Long empId = config.getEmployee() != null ? config.getEmployee().getId() : null;
        Long waiterId = config.getWaiter() != null ? config.getWaiter().getId() : null;
        return all.stream().filter(s -> {
            boolean empMatch = empId != null && s.getEmployee() != null
                    && empId.equals(s.getEmployee().getId());
            boolean waiterMatch = waiterId != null && s.getWaiter() != null
                    && waiterId.equals(s.getWaiter().getId());
            return empMatch || waiterMatch;
        }).count();
    }

    private boolean alreadyPaidForPeriod(SalaryConfig config, Period period) {
        if (config.getLastPaidDate() == null) return false;
        // We treat lastPaidDate as the period-end of the last successful
        // payment. Any subsequent attempt against an earlier-or-equal
        // period-end is a duplicate.
        return !config.getLastPaidDate().isBefore(period.end);
    }

    private String buildNote(PayFrequency freq, Period period, BigDecimal advanceDeduction) {
        String base = "Auto-generated " + freq + " salary (" + period.start + " → " + period.end + ")";
        if (advanceDeduction != null && advanceDeduction.compareTo(BigDecimal.ZERO) > 0) {
            return base + ". Advance deducted: " + advanceDeduction;
        }
        return base;
    }

    private static int clampDayOfMonth(int requested, LocalDate ref) {
        int lengthOfMonth = ref.lengthOfMonth();
        return Math.min(Math.max(requested, 1), lengthOfMonth);
    }

    private BigDecimal calculateUnpaidAdvances(Long restaurantId, Long employeeId,
                                                LocalDate periodStart, LocalDate periodEnd) {
        List<PayrollEntry> advances = payrollEntryRepository
                .findByRestaurant_IdAndPayPeriodStartBetween(restaurantId, periodStart, periodEnd)
                .stream()
                .filter(p -> p.getEmployee() != null && p.getEmployee().getId().equals(employeeId))
                .filter(p -> p.getPayrollType() == PayrollEntry.PayrollType.ADVANCE)
                .filter(p -> p.getStatus() == PayrollEntry.PaymentStatus.PAID)
                .toList();
        return advances.stream()
                .map(PayrollEntry::getNetPay)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record Period(LocalDate start, LocalDate end) {}

    // DayOfWeek is referenced indirectly through Integer comparison above;
    // keeping the import live in case a future tweak needs the type.
    @SuppressWarnings("unused")
    private static int dowAsInt(DayOfWeek d) { return d.getValue(); }
}
