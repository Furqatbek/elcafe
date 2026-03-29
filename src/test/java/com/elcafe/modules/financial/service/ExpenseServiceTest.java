package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private JournalService journalService;

    @InjectMocks private ExpenseService expenseService;

    private Expense expense;

    @BeforeEach
    void setUp() {
        Restaurant restaurant = createRestaurant();
        expense = new Expense();
        expense.setId(1L);
        expense.setRestaurant(restaurant);
        expense.setAmount(BigDecimal.valueOf(50000));
        expense.setCategory(Expense.ExpenseCategory.FOOD);
        expense.setDescription("Vegetables");
        expense.setExpenseDate(LocalDate.now());
    }

    @Test
    @DisplayName("createExpense — saves and returns")
    void createExpense_saves() {
        when(expenseRepository.save(any(Expense.class))).thenReturn(expense);
        Expense result = expenseService.createExpense(expense);
        assertNotNull(result);
        verify(expenseRepository).save(expense);
    }

    @Test
    @DisplayName("getExpenseById — found")
    void getExpenseById_found() {
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
        assertEquals(1L, expenseService.getExpenseById(1L).getId());
    }

    @Test
    @DisplayName("getExpenseById — not found throws")
    void getExpenseById_notFound_throws() {
        when(expenseRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(Exception.class, () -> expenseService.getExpenseById(99L));
    }

    @Test
    @DisplayName("getExpensesByRestaurant — returns list")
    void getExpensesByRestaurant_returnsList() {
        when(expenseRepository.findByRestaurantIdOrderByExpenseDateDesc(1L)).thenReturn(List.of(expense));
        assertEquals(1, expenseService.getExpensesByRestaurant(1L).size());
    }

    @Test
    @DisplayName("getExpensesByCategory — filters correctly")
    void getExpensesByCategory_filters() {
        when(expenseRepository.findByRestaurantIdAndCategoryOrderByExpenseDateDesc(1L, Expense.ExpenseCategory.FOOD))
                .thenReturn(List.of(expense));
        assertEquals(1, expenseService.getExpensesByCategory(1L, Expense.ExpenseCategory.FOOD).size());
    }

    @Test
    @DisplayName("getExpensesByDateRange — returns filtered list")
    void getExpensesByDateRange_returns() {
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();
        when(expenseRepository.findByRestaurantIdAndExpenseDateBetweenOrderByExpenseDateDesc(1L, start, end))
                .thenReturn(List.of(expense));
        assertEquals(1, expenseService.getExpensesByDateRange(1L, start, end).size());
    }

    @Test
    @DisplayName("getTotalExpensesByDateRange — returns sum")
    void getTotalExpensesByDateRange_returnsSum() {
        when(expenseRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(BigDecimal.valueOf(150000));
        assertEquals(0, BigDecimal.valueOf(150000).compareTo(
                expenseService.getTotalExpensesByDateRange(1L, LocalDate.now().minusDays(7), LocalDate.now())));
    }

    @Test
    @DisplayName("approveExpense — sets approved status")
    void approveExpense_setsApproved() {
        expense.setStatus(Expense.ExpenseStatus.PENDING);
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> i.getArgument(0));

        Expense result = expenseService.approveExpense(1L, "admin");
        assertEquals(Expense.ExpenseStatus.APPROVED, result.getStatus());
    }

    @Test
    @DisplayName("deleteExpense — soft deletes")
    void deleteExpense_softDeletes() {
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> i.getArgument(0));

        expenseService.deleteExpense(1L, "admin");
        verify(expenseRepository).save(any(Expense.class));
    }

    @Test
    @DisplayName("getUnpaidExpenses — returns unpaid only")
    void getUnpaidExpenses_returns() {
        when(expenseRepository.findByRestaurantIdAndPaymentDateIsNullOrderByExpenseDateDesc(1L))
                .thenReturn(List.of(expense));
        assertEquals(1, expenseService.getUnpaidExpenses(1L).size());
    }
}
