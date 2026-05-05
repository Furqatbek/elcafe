package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.entity.SalaryConfig;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.financial.repository.SalaryConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryAutoPayService {

    private final SalaryConfigRepository salaryConfigRepository;
    private final PayrollEntryRepository payrollEntryRepository;
    private final PayrollService payrollService;

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void processScheduledSalaries() {
        LocalDate today = LocalDate.now();
        int dayOfMonth = today.getDayOfMonth();

        List<SalaryConfig> dueConfigs = salaryConfigRepository.findDueForPayment(dayOfMonth, today);

        if (dueConfigs.isEmpty()) {
            log.debug("No salary payments due today (day {})", dayOfMonth);
            return;
        }

        log.info("Processing {} salary payments for day {}", dueConfigs.size(), dayOfMonth);

        for (SalaryConfig config : dueConfigs) {
            try {
                processPayment(config, LocalDate.now());
            } catch (Exception e) {
                log.error("Failed to process salary for employee {} at restaurant {}: {}",
                        config.getEmployee().getId(), config.getRestaurant().getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void processPayment(SalaryConfig config, LocalDate paymentDate) {
        // Prevent double payment in same month
        if (config.getLastPaidDate() != null
                && config.getLastPaidDate().getMonth() == paymentDate.getMonth()
                && config.getLastPaidDate().getYear() == paymentDate.getYear()) {
            throw new IllegalStateException("Salary already paid this month (last paid: " + config.getLastPaidDate() + ")");
        }

        LocalDate periodStart = paymentDate.minusMonths(1).withDayOfMonth(config.getPayDay());
        LocalDate periodEnd = paymentDate.minusDays(1);

        // Calculate unpaid advances for this employee in this period
        Long empId = config.getEmployee() != null ? config.getEmployee().getId() : null;
        BigDecimal advanceDeduction = empId != null
                ? calculateUnpaidAdvances(config.getRestaurant().getId(), empId, periodStart, periodEnd)
                : BigDecimal.ZERO;

        PayrollEntry entry = PayrollEntry.builder()
                .restaurant(config.getRestaurant())
                .employee(config.getEmployee())
                .waiter(config.getWaiter())
                .payrollType(PayrollEntry.PayrollType.SALARY)
                .payPeriodStart(periodStart)
                .payPeriodEnd(periodEnd)
                .baseSalary(config.getMonthlySalary())
                .otherDeductions(advanceDeduction)
                .notes(advanceDeduction.compareTo(BigDecimal.ZERO) > 0
                        ? "Auto-generated. Advance deducted: " + advanceDeduction
                        : "Auto-generated fixed salary payment")
                .build();

        PayrollEntry saved = payrollService.createPayrollEntry(entry);

        if (config.getAutoApprove()) {
            payrollService.approvePayrollEntry(saved.getId(), "System");
            payrollService.processPayment(saved.getId(), paymentDate, config.getPaymentMethod(), null);
        }

        config.setLastPaidDate(paymentDate);
        salaryConfigRepository.save(config);

        String employeeName = config.getEmployee() != null
                ? config.getEmployee().getEmail()
                : config.getWaiter() != null ? config.getWaiter().getName() : "unknown";
        log.info("Salary processed for {}: {} - advances {} = net on {}",
                employeeName, config.getMonthlySalary(), advanceDeduction, paymentDate);
    }

    private BigDecimal calculateUnpaidAdvances(Long restaurantId, Long employeeId,
                                                LocalDate periodStart, LocalDate periodEnd) {
        List<PayrollEntry> advances = payrollEntryRepository
                .findByRestaurant_IdAndPayPeriodStartBetween(restaurantId, periodStart, periodEnd)
                .stream()
                .filter(p -> p.getEmployee().getId().equals(employeeId))
                .filter(p -> p.getPayrollType() == PayrollEntry.PayrollType.ADVANCE)
                .filter(p -> p.getStatus() == PayrollEntry.PaymentStatus.PAID)
                .toList();

        return advances.stream()
                .map(PayrollEntry::getNetPay)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
