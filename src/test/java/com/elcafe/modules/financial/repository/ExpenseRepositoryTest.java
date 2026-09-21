package com.elcafe.modules.financial.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.entity.Expense.ExpenseCategory;
import com.elcafe.modules.financial.entity.Expense.PaymentStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class ExpenseRepositoryTest {

    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);
    }

    private Expense createExpense(String number, PaymentStatus status, BigDecimal amount, LocalDate date) {
        Expense expense = Expense.builder()
                .restaurant(restaurant)
                .expenseNumber(number)
                .expenseDate(date)
                .category(ExpenseCategory.SUPPLIES)
                .description("Test expense " + number)
                .amount(amount)
                .totalAmount(amount)
                .paymentStatus(status)
                .recurring(false)
                .build();
        em.persist(expense);
        return expense;
    }

    @Test
    @DisplayName("findUnpaidExpenses returns only UNPAID expenses ordered by date ASC")
    void findUnpaidExpenses() {
        createExpense("EXP-001", PaymentStatus.UNPAID, new BigDecimal("100.00"), LocalDate.of(2025, 3, 15));
        createExpense("EXP-002", PaymentStatus.PAID, new BigDecimal("200.00"), LocalDate.of(2025, 3, 10));
        createExpense("EXP-003", PaymentStatus.UNPAID, new BigDecimal("50.00"), LocalDate.of(2025, 3, 5));

        em.flush();
        em.clear();

        List<Expense> unpaid = expenseRepository.findUnpaidExpenses(restaurant.getId());
        assertEquals(2, unpaid.size());
        // Should be ordered by expenseDate ASC
        assertTrue(unpaid.get(0).getExpenseDate().isBefore(unpaid.get(1).getExpenseDate()));
        assertTrue(unpaid.stream().allMatch(e -> e.getPaymentStatus() == PaymentStatus.UNPAID));
    }

    @Test
    @DisplayName("getTotalExpensesByDateRange sums only PAID expense totals in range")
    void getTotalExpensesByDateRange() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);

        createExpense("EXP-010", PaymentStatus.PAID, new BigDecimal("100.00"), LocalDate.of(2025, 1, 10));
        createExpense("EXP-011", PaymentStatus.PAID, new BigDecimal("200.00"), LocalDate.of(2025, 1, 20));
        // UNPAID should not count
        createExpense("EXP-012", PaymentStatus.UNPAID, new BigDecimal("300.00"), LocalDate.of(2025, 1, 15));
        // Outside date range should not count
        createExpense("EXP-013", PaymentStatus.PAID, new BigDecimal("500.00"), LocalDate.of(2025, 2, 1));

        em.flush();
        em.clear();

        BigDecimal total = expenseRepository.getTotalExpensesByDateRange(restaurant.getId(), start, end);
        assertEquals(0, new BigDecimal("300.00").compareTo(total));
    }
}
