package com.elcafe.modules.financial.repository;

import com.elcafe.modules.financial.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId);

    Page<Expense> findByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    Optional<Expense> findByExpenseNumber(String expenseNumber);

    List<Expense> findByRestaurant_IdAndCategory(Long restaurantId, Expense.ExpenseCategory category);

    List<Expense> findByRestaurant_IdAndPaymentStatus(Long restaurantId, Expense.PaymentStatus paymentStatus);

    List<Expense> findByRestaurant_IdAndExpenseDateBetween(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    /**
     * Finds expenses recorded (createdAt) within a precise timestamp
     * window. Used by reports/notifications that need to align expenses
     * with a shift's actual operational hours rather than the calendar
     * day — a 06:00–02:00 shift should not include expenses recorded
     * at 03:00 just because they share a calendar date with the
     * shift's open hour.
     */
    List<Expense> findByRestaurant_IdAndCreatedAtBetween(
            Long restaurantId, LocalDateTime startInclusive, LocalDateTime endInclusive);

    List<Expense> findByRestaurant_IdAndRecurringTrue(Long restaurantId);

    @Query("SELECT e FROM FinancialExpense e WHERE e.restaurant.id = :restaurantId " +
           "AND e.paymentStatus = 'UNPAID' " +
           "ORDER BY e.expenseDate ASC")
    List<Expense> findUnpaidExpenses(Long restaurantId);

    @Query("SELECT e FROM FinancialExpense e WHERE e.restaurant.id = :restaurantId " +
           "AND e.paymentStatus = 'OVERDUE' " +
           "ORDER BY e.expenseDate ASC")
    List<Expense> findOverdueExpenses(Long restaurantId);

    @Query("SELECT e FROM FinancialExpense e WHERE e.restaurant.id = :restaurantId " +
           "AND e.expenseDate BETWEEN :startDate AND :endDate " +
           "AND e.category = :category")
    List<Expense> findByRestaurantAndDateRangeAndCategory(
            Long restaurantId, LocalDate startDate, LocalDate endDate, Expense.ExpenseCategory category);

    @Query("SELECT SUM(e.totalAmount) FROM FinancialExpense e WHERE e.restaurant.id = :restaurantId " +
           "AND e.expenseDate BETWEEN :startDate AND :endDate " +
           "AND e.paymentStatus = 'PAID'")
    BigDecimal getTotalExpensesByDateRange(Long restaurantId, LocalDate startDate, LocalDate endDate);

    /**
     * Sum paid expenses tied to a specific employee_shift_id, split by
     * whether they were paid from the shift's cash drawer.
     *
     * Used by the shift-closed Telegram notification so the message
     * shows the closing shift's own expenses, not the whole day's
     * restaurant totals — a 0-minute "ghost" shift should report 0,
     * not inherit the day's expense bucket.
     */
    @Query("SELECT COALESCE(SUM(e.totalAmount), 0) FROM FinancialExpense e " +
           "WHERE e.employeeShift.id = :shiftId " +
           "AND e.paymentStatus = 'PAID' " +
           "AND e.paidFromShiftDrawer = true")
    BigDecimal sumDrawerExpensesByShift(Long shiftId);

    @Query("SELECT COALESCE(SUM(e.totalAmount), 0) FROM FinancialExpense e " +
           "WHERE e.employeeShift.id = :shiftId " +
           "AND e.paymentStatus = 'PAID' " +
           "AND (e.paidFromShiftDrawer = false OR e.paidFromShiftDrawer IS NULL)")
    BigDecimal sumNonDrawerExpensesByShift(Long shiftId);

    @Query("SELECT e.category, SUM(e.totalAmount) FROM FinancialExpense e " +
           "WHERE e.restaurant.id = :restaurantId " +
           "AND e.expenseDate BETWEEN :startDate AND :endDate " +
           "AND e.paymentStatus = 'PAID' " +
           "GROUP BY e.category")
    List<Object[]> getExpensesByCategory(Long restaurantId, LocalDate startDate, LocalDate endDate);

    Optional<Expense> findByPurchaseOrderId(Long purchaseOrderId);
}
