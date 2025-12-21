package com.elcafe.modules.financial.repository;

import com.elcafe.modules.financial.entity.Expense;
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
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByRestaurantId(Long restaurantId);

    Page<Expense> findByRestaurantId(Long restaurantId, Pageable pageable);

    Optional<Expense> findByExpenseNumber(String expenseNumber);

    List<Expense> findByRestaurantIdAndCategory(Long restaurantId, Expense.ExpenseCategory category);

    List<Expense> findByRestaurantIdAndPaymentStatus(Long restaurantId, Expense.PaymentStatus paymentStatus);

    List<Expense> findByRestaurantIdAndExpenseDateBetween(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    List<Expense> findByRestaurantIdAndRecurringTrue(Long restaurantId);

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

    @Query("SELECT e.category, SUM(e.totalAmount) FROM FinancialExpense e " +
           "WHERE e.restaurant.id = :restaurantId " +
           "AND e.expenseDate BETWEEN :startDate AND :endDate " +
           "AND e.paymentStatus = 'PAID' " +
           "GROUP BY e.category")
    List<Object[]> getExpensesByCategory(Long restaurantId, LocalDate startDate, LocalDate endDate);

    Optional<Expense> findByPurchaseOrderId(Long purchaseOrderId);
}
