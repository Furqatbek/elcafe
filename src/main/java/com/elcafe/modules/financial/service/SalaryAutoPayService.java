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
                processPayment(config, today, false);
                processed++;
            } catch (Exception e) {
                log.error("Salary payment failed for config {} ({}): {}",
                        config.getId(), config.getPayFrequency(), e.getMessage());
            }
        }
        log.info("Auto-pay run on {}: {}/{} configs paid", today, processed, candidates.size());
    }

    /**
     * Fire-on-clock-out hook for PER_SHIFT configs. Creates a single
     * PayrollEntry for the just-closed shift (period = [shiftDate,
     * shiftDate], amount = config.baseAmount × 1) and flips the shift's
     * paidForSalary flag so the 08:00 batcher won't pay it again. No-op
     * for non-PER_SHIFT configs and for shifts that are already marked
     * paid.
     *
     * Designed to be called from inside the clock-out transaction. If
     * any payroll write fails the whole clock-out rolls back — that's
     * intentional, an unpaid shift is a louder bug than a blocked
     * clock-out and the cashier can retry.
     */
    @Transactional
    public void processClockOut(EmployeeShift shift) {
        if (shift == null || Boolean.TRUE.equals(shift.getPaidForSalary())) return;
        if (shift.getClockOut() == null) return;

        Long restaurantId = shift.getRestaurant() != null ? shift.getRestaurant().getId() : null;
        if (restaurantId == null) return;

        List<SalaryConfig> configs = salaryConfigRepository
                .findByRestaurant_IdAndActiveTrue(restaurantId)
                .stream()
                .filter(c -> c.getPayFrequency() == PayFrequency.PER_SHIFT)
                .filter(c -> matchesShiftSubject(c, shift))
                .toList();

        if (configs.isEmpty()) return;

        Period period = new Period(shift.getShiftDate(), shift.getShiftDate());
        for (SalaryConfig config : configs) {
            BigDecimal base = config.effectiveBaseAmount();
            if (base.compareTo(BigDecimal.ZERO) <= 0) continue;

            Long empId = config.getEmployee() != null ? config.getEmployee().getId() : null;
            BigDecimal advanceDeduction = empId != null
                    ? calculateUnpaidAdvances(restaurantId, empId, period.start, period.end)
                    : BigDecimal.ZERO;

            PayrollEntry entry = PayrollEntry.builder()
                    .restaurant(config.getRestaurant())
                    .employee(config.getEmployee())
                    .waiter(config.getWaiter())
                    .payrollType(PayrollEntry.PayrollType.SALARY)
                    .payPeriodStart(period.start)
                    .payPeriodEnd(period.end)
                    .baseSalary(base)
                    .otherDeductions(advanceDeduction)
                    .notes("Auto-generated PER_SHIFT payout for shift " + shift.getId()
                            + " (" + shift.getShiftDate() + ")")
                    .build();

            PayrollEntry saved = payrollService.createPayrollEntry(entry);
            if (Boolean.TRUE.equals(config.getAutoApprove())) {
                payrollService.approvePayrollEntry(saved.getId(), "System");
                payrollService.processPayment(saved.getId(), shift.getShiftDate(),
                        config.getPaymentMethod(), null);
            }

            // Keep the config cursor in sync so the cron sees the period
            // as already settled.
            if (config.getLastPaidDate() == null || config.getLastPaidDate().isBefore(period.end)) {
                config.setLastPaidDate(period.end);
                salaryConfigRepository.save(config);
            }
        }

        shift.setPaidForSalary(true);
        shiftRepository.save(shift);
        log.info("PER_SHIFT auto-payout fired for shift {} ({} configs paid)",
                shift.getId(), configs.size());
    }

    private boolean matchesShiftSubject(SalaryConfig config, EmployeeShift shift) {
        Long cfgEmp = config.getEmployee() != null ? config.getEmployee().getId() : null;
        Long cfgWtr = config.getWaiter() != null ? config.getWaiter().getId() : null;
        Long shEmp = shift.getEmployee() != null ? shift.getEmployee().getId() : null;
        Long shWtr = shift.getWaiter() != null ? shift.getWaiter().getId() : null;
        if (cfgEmp != null && cfgEmp.equals(shEmp)) return true;
        if (cfgWtr != null && cfgWtr.equals(shWtr)) return true;
        return false;
    }

    /**
     * Manual "pay now" trigger from the admin UI. Bypasses the
     * isDueToday check (operators may want to pay early) but still
     * applies the same per-frequency period and double-payment guards.
     */
    @Transactional
    public void processPayment(SalaryConfig config, LocalDate paymentDate) {
        processPayment(config, paymentDate, true);
    }

    /**
     * @param manual  true when triggered by the admin "Pay Now" button. The
     *                cron passes false so empty-period configs get silently
     *                advanced; the manual path instead surfaces an error and
     *                leaves the cursor alone so the operator can retry once
     *                the employee actually clocks a shift.
     */
    @Transactional
    public void processPayment(SalaryConfig config, LocalDate paymentDate, boolean manual) {
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
            if (manual) {
                // Tell the operator what's going on instead of silently
                // doing nothing and disabling the button — they explicitly
                // asked to pay and the result was no PayrollEntry, which
                // looks like the system swallowed the action.
                throw new IllegalStateException(
                        "Nothing to pay for " + period.start + " — " + period.end
                                + ": no clocked shifts in this period.");
            }
            log.info("Skipping {} payment for config {} — nothing to pay ({} earned in period)",
                    freq, config.getId(), baseAmount);
            // For the cron path, advance the cursor so we don't keep
            // re-evaluating the same empty period on every run.
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

        // For shift-driven frequencies, flip the paidForSalary flag on every
        // shift the entry covers so the fire-on-clock-out hook never
        // re-settles them and the next batcher pass ignores them.
        if (freq == PayFrequency.DAILY || freq == PayFrequency.PER_SHIFT) {
            for (EmployeeShift s : matchingShiftsInPeriod(config, period)) {
                s.setPaidForSalary(true);
                shiftRepository.save(s);
            }
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
        return matchingShiftsInPeriod(config, period).size();
    }

    private List<EmployeeShift> matchingShiftsInPeriod(SalaryConfig config, Period period) {
        List<EmployeeShift> all = shiftRepository.findByRestaurantAndDateRange(
                config.getRestaurant().getId(), period.start, period.end);
        Long empId = config.getEmployee() != null ? config.getEmployee().getId() : null;
        Long waiterId = config.getWaiter() != null ? config.getWaiter().getId() : null;
        return all.stream().filter(s -> {
            // Skip shifts already settled by the fire-on-clock-out hook so
            // the batcher never double-pays.
            if (Boolean.TRUE.equals(s.getPaidForSalary())) return false;
            boolean empMatch = empId != null && s.getEmployee() != null
                    && empId.equals(s.getEmployee().getId());
            boolean waiterMatch = waiterId != null && s.getWaiter() != null
                    && waiterId.equals(s.getWaiter().getId());
            return empMatch || waiterMatch;
        }).toList();
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
