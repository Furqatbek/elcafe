package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollEntryRepository payrollRepository;
    private final RestaurantRepository restaurantRepository;
    private final AccountRepository accountRepository;
    private final JournalService journalService;

    @Transactional
    public PayrollEntry createPayrollEntry(PayrollEntry payrollEntry) {
        log.info("Creating payroll entry for subject: {}", subjectLabel(payrollEntry));

        String payrollNumber = generatePayrollNumber(payrollEntry.getRestaurant().getId());
        payrollEntry.setPayrollNumber(payrollNumber);
        payrollEntry.setStatus(PayrollEntry.PaymentStatus.PENDING);

        // Calculations are done via @PrePersist method in entity
        PayrollEntry savedPayroll = payrollRepository.save(payrollEntry);

        log.info("Payroll entry created: {}", payrollNumber);
        return savedPayroll;
    }

    /**
     * Post an over-allowance consumption charge as a salary advance in
     * its OWN transaction so a failure here can never poison the caller's
     * transaction (e.g. EmployeeConsumptionService.recordConsumption). The
     * caller logs+swallows our exception; we make sure that swallow is
     * actually effective by isolating us with REQUIRES_NEW.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public PayrollEntry postOverAllowanceAdvance(PayrollEntry entry,
                                                 java.time.LocalDate paymentDate,
                                                 PayrollEntry.PaymentMethod paymentMethod) {
        PayrollEntry saved = createPayrollEntry(entry);
        approvePayrollEntry(saved.getId(), "System");
        return processPayment(saved.getId(), paymentDate, paymentMethod, null);
    }

    /** Best-effort name for a payroll subject — works for waiter-only entries. */
    private String subjectLabel(PayrollEntry e) {
        if (e.getEmployee() != null) {
            String name = e.getEmployee().getFullName();
            if (name != null && !name.isBlank()) return name;
            return e.getEmployee().getEmail();
        }
        if (e.getWaiter() != null) {
            String name = e.getWaiter().getName();
            if (name != null && !name.isBlank()) return name;
            return "waiter#" + e.getWaiter().getId();
        }
        return "(unknown)";
    }

    @Transactional
    public PayrollEntry approvePayrollEntry(Long payrollId, String approvedBy) {
        log.info("Approving payroll entry: {}", payrollId);

        PayrollEntry payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll entry not found"));

        if (payroll.getStatus() != PayrollEntry.PaymentStatus.PENDING) {
            throw new RuntimeException("Only pending payroll entries can be approved");
        }

        payroll.setStatus(PayrollEntry.PaymentStatus.APPROVED);
        payroll.setApprovedBy(approvedBy);
        payroll.setApprovedAt(LocalDateTime.now());

        return payrollRepository.save(payroll);
    }

    @Transactional
    public PayrollEntry processPayment(Long payrollId, LocalDate paymentDate,
                                      PayrollEntry.PaymentMethod paymentMethod,
                                      String processedBy) {
        log.info("Processing payment for payroll entry: {}", payrollId);

        PayrollEntry payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll entry not found"));

        if (payroll.getStatus() != PayrollEntry.PaymentStatus.APPROVED) {
            throw new RuntimeException("Only approved payroll entries can be paid");
        }

        payroll.setPaymentDate(paymentDate);
        payroll.setPaymentMethod(paymentMethod);
        payroll.setStatus(PayrollEntry.PaymentStatus.PAID);
        payroll.setProcessedBy(processedBy);
        payroll.setProcessedAt(LocalDateTime.now());

        PayrollEntry savedPayroll = payrollRepository.save(payroll);

        // Create journal entry
        createPayrollJournalEntry(savedPayroll, processedBy);

        log.info("Payment processed for payroll: {}", payroll.getPayrollNumber());
        return savedPayroll;
    }

    /**
     * Soft delete a payroll entry. Financial records should never be hard deleted for audit compliance.
     *
     * @param id The payroll entry ID to delete
     * @param deletedBy Username of the person performing the deletion
     */
    @Transactional
    public void deletePayrollEntry(Long id, String deletedBy) {
        log.info("Soft deleting payroll entry: {} by user: {}", id, deletedBy);

        PayrollEntry payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payroll entry not found"));

        if (payroll.isDeleted()) {
            throw new RuntimeException("Payroll entry has already been deleted");
        }

        if (payroll.getStatus() == PayrollEntry.PaymentStatus.PAID) {
            throw new RuntimeException("Cannot delete paid payroll entry - use void/reverse instead");
        }

        // Use soft delete instead of hard delete for audit compliance
        payroll.softDelete(deletedBy);
        payrollRepository.save(payroll);
        log.info("Soft deleted payroll entry: {} by user: {}", payroll.getPayrollNumber(), deletedBy);
    }

    public PayrollEntry getPayrollEntryById(Long id) {
        return payrollRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payroll entry not found"));
    }

    public List<PayrollEntry> getPayrollEntriesByRestaurant(Long restaurantId) {
        return payrollRepository.findByRestaurant_Id(restaurantId);
    }

    public List<PayrollEntry> getPayrollEntriesByEmployee(Long employeeId) {
        return payrollRepository.findByEmployee_Id(employeeId);
    }

    public List<PayrollEntry> getPayrollEntriesByDateRange(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        return payrollRepository.findByRestaurant_IdAndPayPeriodStartBetween(restaurantId, startDate, endDate);
    }

    public List<PayrollEntry> getPendingPayrolls(Long restaurantId) {
        return payrollRepository.findPendingPayrolls(restaurantId);
    }

    public List<PayrollEntry> getApprovedButUnpaid(Long restaurantId) {
        return payrollRepository.findApprovedButUnpaid(restaurantId);
    }

    public BigDecimal getTotalPayrollByDateRange(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        BigDecimal total = payrollRepository.getTotalPayrollByDateRange(restaurantId, startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    private void createPayrollJournalEntry(PayrollEntry payroll, String processedBy) {
        try {
            // Debit: Labor Expense, Credit: Cash/Bank
            Account laborAccount = accountRepository.findByRestaurant_IdAndCategory(
                    payroll.getRestaurant().getId(), Account.AccountCategory.LABOR
            ).stream().findFirst().orElse(null);

            Account.AccountCategory paymentCategory = payroll.getPaymentMethod() == PayrollEntry.PaymentMethod.CASH
                    ? Account.AccountCategory.CASH
                    : Account.AccountCategory.BANK;

            Account paymentAccount = accountRepository.findByRestaurant_IdAndCategory(
                    payroll.getRestaurant().getId(), paymentCategory
            ).stream().findFirst().orElse(null);

            if (laborAccount != null && paymentAccount != null) {
                journalService.createJournalEntry(
                        payroll.getRestaurant().getId(),
                        payroll.getPaymentDate(),
                        "Payroll: " + subjectLabel(payroll) +
                                " (" + payroll.getPayPeriodStart() + " to " + payroll.getPayPeriodEnd() + ")",
                        "PAYROLL",
                        payroll.getId(),
                        laborAccount.getId(),
                        paymentAccount.getId(),
                        payroll.getNetPay(),
                        processedBy
                );
            }
        } catch (Exception e) {
            log.warn("Failed to create payroll journal entry: {}", e.getMessage());
        }
    }

    private String generatePayrollNumber(Long restaurantId) {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long count = payrollRepository.findByRestaurant_Id(restaurantId).stream()
                .filter(pr -> pr.getPayrollNumber().startsWith("PAY-" + datePrefix))
                .count();
        return String.format("PAY-%s-%04d", datePrefix, count + 1);
    }
}
