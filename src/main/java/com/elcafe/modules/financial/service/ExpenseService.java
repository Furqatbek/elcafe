package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final RestaurantRepository restaurantRepository;
    private final AccountRepository accountRepository;
    private final JournalService journalService;

    @Transactional
    public Expense createExpense(Expense expense) {
        log.info("Creating expense for restaurant: {}", expense.getRestaurant().getId());

        String expenseNumber = generateExpenseNumber(expense.getRestaurant().getId());
        expense.setExpenseNumber(expenseNumber);
        expense.setPaymentStatus(Expense.PaymentStatus.UNPAID);

        Expense savedExpense = expenseRepository.save(expense);

        log.info("Expense created: {}", expenseNumber);
        return savedExpense;
    }

    @Transactional
    public Expense approveExpense(Long expenseId, String approvedBy) {
        log.info("Approving expense: {}", expenseId);

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        expense.setApprovedBy(approvedBy);
        expense.setApprovedAt(java.time.LocalDateTime.now());

        return expenseRepository.save(expense);
    }

    @Transactional
    public Expense recordPayment(Long expenseId, LocalDate paymentDate, String recordedBy) {
        log.info("Recording payment for expense: {}", expenseId);

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        expense.setPaymentDate(paymentDate);
        expense.setPaymentStatus(Expense.PaymentStatus.PAID);

        Expense savedExpense = expenseRepository.save(expense);

        // Create journal entry
        createExpenseJournalEntry(savedExpense, recordedBy);

        log.info("Payment recorded for expense: {}", expense.getExpenseNumber());
        return savedExpense;
    }

    @Transactional
    public void deleteExpense(Long id) {
        log.info("Deleting expense: {}", id);

        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        if (expense.getPaymentStatus() == Expense.PaymentStatus.PAID) {
            throw new RuntimeException("Cannot delete paid expense");
        }

        expenseRepository.delete(expense);
    }

    public Expense getExpenseById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
    }

    public List<Expense> getExpensesByRestaurant(Long restaurantId) {
        return expenseRepository.findByRestaurantId(restaurantId);
    }

    public List<Expense> getExpensesByCategory(Long restaurantId, Expense.ExpenseCategory category) {
        return expenseRepository.findByRestaurantIdAndCategory(restaurantId, category);
    }

    public List<Expense> getExpensesByDateRange(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        return expenseRepository.findByRestaurantIdAndExpenseDateBetween(restaurantId, startDate, endDate);
    }

    public List<Expense> getUnpaidExpenses(Long restaurantId) {
        return expenseRepository.findUnpaidExpenses(restaurantId);
    }

    public BigDecimal getTotalExpensesByDateRange(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        BigDecimal total = expenseRepository.getTotalExpensesByDateRange(restaurantId, startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    private void createExpenseJournalEntry(Expense expense, String recordedBy) {
        try {
            // Debit: Expense Account, Credit: Cash/Bank
            Account expenseAccount = expense.getAccount();
            if (expenseAccount == null) {
                // Find default expense account based on category
                expenseAccount = findExpenseAccountByCategory(
                        expense.getRestaurant().getId(),
                        expense.getCategory()
                );
            }

            Account.AccountCategory paymentCategory = expense.getPaymentMethod() == Expense.PaymentMethod.CASH
                    ? Account.AccountCategory.CASH
                    : Account.AccountCategory.BANK;

            Account paymentAccount = accountRepository.findByRestaurantIdAndCategory(
                    expense.getRestaurant().getId(), paymentCategory
            ).stream().findFirst().orElse(null);

            if (expenseAccount != null && paymentAccount != null) {
                journalService.createJournalEntry(
                        expense.getRestaurant().getId(),
                        expense.getPaymentDate(),
                        "Expense: " + expense.getDescription(),
                        "EXPENSE",
                        expense.getId(),
                        expenseAccount.getId(),
                        paymentAccount.getId(),
                        expense.getTotalAmount(),
                        recordedBy
                );
            }
        } catch (Exception e) {
            log.warn("Failed to create expense journal entry: {}", e.getMessage());
        }
    }

    private Account findExpenseAccountByCategory(Long restaurantId, Expense.ExpenseCategory category) {
        Account.AccountCategory accountCategory = switch (category) {
            case RENT -> Account.AccountCategory.RENT;
            case UTILITIES -> Account.AccountCategory.UTILITIES;
            case SUPPLIES -> Account.AccountCategory.SUPPLIES;
            case MARKETING -> Account.AccountCategory.MARKETING;
            case DELIVERY_COSTS -> Account.AccountCategory.DELIVERY_COSTS;
            default -> Account.AccountCategory.OTHER_EXPENSE;
        };

        return accountRepository.findByRestaurantIdAndCategory(restaurantId, accountCategory)
                .stream().findFirst().orElse(null);
    }

    private String generateExpenseNumber(Long restaurantId) {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long count = expenseRepository.findByRestaurantId(restaurantId).stream()
                .filter(exp -> exp.getExpenseNumber().startsWith("EXP-" + datePrefix))
                .count();
        return String.format("EXP-%s-%04d", datePrefix, count + 1);
    }
}
