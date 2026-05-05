package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.entity.SalaryConfig;
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
                processPayment(config, today);
            } catch (Exception e) {
                log.error("Failed to process salary for employee {} at restaurant {}: {}",
                        config.getEmployee().getId(), config.getRestaurant().getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void processPayment(SalaryConfig config, LocalDate paymentDate) {
        LocalDate periodStart = paymentDate.minusMonths(1).withDayOfMonth(config.getPayDay());
        LocalDate periodEnd = paymentDate.minusDays(1);

        PayrollEntry entry = PayrollEntry.builder()
                .restaurant(config.getRestaurant())
                .employee(config.getEmployee())
                .payrollType(PayrollEntry.PayrollType.SALARY)
                .payPeriodStart(periodStart)
                .payPeriodEnd(periodEnd)
                .baseSalary(config.getMonthlySalary())
                .notes("Auto-generated fixed salary payment")
                .build();

        PayrollEntry saved = payrollService.createPayrollEntry(entry);

        if (config.getAutoApprove()) {
            payrollService.approvePayrollEntry(saved.getId(), "System");
            payrollService.processPayment(saved.getId(), paymentDate, config.getPaymentMethod(), null);
        }

        config.setLastPaidDate(paymentDate);
        salaryConfigRepository.save(config);

        log.info("Salary processed for employee {} ({}): {} on {}",
                config.getEmployee().getId(),
                config.getEmployee().getEmail(),
                config.getMonthlySalary(), paymentDate);
    }
}
