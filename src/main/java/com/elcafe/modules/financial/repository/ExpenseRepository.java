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

    List<Expense> findByRestaurant_Id(Long restaurantId);

    Page<Expense> findByRestaurant_Id(Long restaurantId, Pageable pageable);

    Optional<Expense> findByExpenseNumber(String expenseNumber);

    List<Expense> findByRestaurant_IdAndCategory(Long restaurantId, Expense.ExpenseCategory category);

    List<Expense> findByRestaurant_IdAndPaymentStatus(Long restaurantId, Expense.PaymentStatus paymentStatus);

    List<Expense> findByRestaurant_IdAndExpenseDateBetween(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

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

    @Query("SELECT e.category, SUM(e.totalAmount) FROM FinancialExpense e " +
           "WHERE e.restaurant.id = :restaurantId " +
           "AND e.expenseDate BETWEEN :startDate AND :endDate " +
           "AND e.paymentStatus = 'PAID' " +
           "GROUP BY e.category")
    List<Object[]> getExpensesByCategory(Long restaurantId, LocalDate startDate, LocalDate endDate);

    Optional<Expense> findByPurchaseOrderId(Long purchaseOrderId);
}
