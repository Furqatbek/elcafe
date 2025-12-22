package com.elcafe.modules.financial.repository;

import com.elcafe.modules.financial.entity.PayrollEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollEntryRepository extends JpaRepository<PayrollEntry, Long> {

    List<PayrollEntry> findByRestaurantId(Long restaurantId);

    Page<PayrollEntry> findByRestaurantId(Long restaurantId, Pageable pageable);

    Optional<PayrollEntry> findByPayrollNumber(String payrollNumber);

    List<PayrollEntry> findByEmployeeId(Long employeeId);

    Page<PayrollEntry> findByEmployeeId(Long employeeId, Pageable pageable);

    List<PayrollEntry> findByRestaurantIdAndStatus(Long restaurantId, PayrollEntry.PaymentStatus status);

    List<PayrollEntry> findByRestaurantIdAndPayPeriodStartBetween(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    List<PayrollEntry> findByRestaurantIdAndPayPeriodEndBetween(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    List<PayrollEntry> findByEmployeeIdAndPayPeriodStartBetween(
            Long employeeId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT pe FROM FinancialPayrollEntry pe WHERE pe.restaurant.id = :restaurantId " +
           "AND pe.payPeriodStart <= :date AND pe.payPeriodEnd >= :date")
    List<PayrollEntry> findByRestaurantAndPayPeriodContaining(Long restaurantId, LocalDate date);

    @Query("SELECT pe FROM FinancialPayrollEntry pe WHERE pe.restaurant.id = :restaurantId " +
           "AND pe.status = 'PENDING' " +
           "ORDER BY pe.payPeriodEnd ASC")
    List<PayrollEntry> findPendingPayrolls(Long restaurantId);

    @Query("SELECT pe FROM FinancialPayrollEntry pe WHERE pe.restaurant.id = :restaurantId " +
           "AND pe.status = 'APPROVED' AND pe.paymentDate IS NULL " +
           "ORDER BY pe.payPeriodEnd ASC")
    List<PayrollEntry> findApprovedButUnpaid(Long restaurantId);

    @Query("SELECT SUM(pe.netPay) FROM FinancialPayrollEntry pe WHERE pe.restaurant.id = :restaurantId " +
           "AND pe.payPeriodStart BETWEEN :startDate AND :endDate " +
           "AND pe.status = 'PAID'")
    BigDecimal getTotalPayrollByDateRange(Long restaurantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT pe.employee.id, pe.employee.email, SUM(pe.netPay) FROM FinancialPayrollEntry pe " +
           "WHERE pe.restaurant.id = :restaurantId " +
           "AND pe.payPeriodStart BETWEEN :startDate AND :endDate " +
           "AND pe.status = 'PAID' " +
           "GROUP BY pe.employee.id, pe.employee.email")
    List<Object[]> getPayrollByEmployee(Long restaurantId, LocalDate startDate, LocalDate endDate);
}
