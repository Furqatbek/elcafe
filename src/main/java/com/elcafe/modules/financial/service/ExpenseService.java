package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        expense.setPaymentStatus(Expense.PaymentStatus.PAID);  // Created as paid
        if (expense.getPaymentDate() == null) {
            expense.setPaymentDate(expense.getExpenseDate());  // Set payment date to expense date
        }

        Expense savedExpense = expenseRepository.save(expense);

        // Create journal entry for the paid expense
        if (expense.getCreatedBy() != null) {
            createExpenseJournalEntry(savedExpense, expense.getCreatedBy());
        }

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

    /**
     * Soft delete an expense. Financial records should never be hard deleted for audit compliance.
     *
     * @param id The expense ID to delete
     * @param deletedBy Username of the person performing the deletion
     */
    @Transactional
    public void deleteExpense(Long id, String deletedBy) {
        log.info("Soft deleting expense: {} by user: {}", id, deletedBy);

        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        if (expense.isDeleted()) {
            throw new RuntimeException("Expense has already been deleted");
        }

        if (expense.getPaymentStatus() == Expense.PaymentStatus.PAID) {
            throw new RuntimeException("Cannot delete paid expense - use void/reverse instead");
        }

        // Use soft delete instead of hard delete for audit compliance
        expense.softDelete(deletedBy);
        expenseRepository.save(expense);
        log.info("Soft deleted expense: {} by user: {}", expense.getExpenseNumber(), deletedBy);
    }

    public Expense getExpenseById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
    }

    public List<Expense> getExpensesByRestaurant(Long restaurantId) {
        return expenseRepository.findByRestaurant_IdOrderByCreatedAtDesc(restaurantId);
    }

    public Page<Expense> getExpensesByRestaurant(Long restaurantId, Pageable pageable) {
        return expenseRepository.findByRestaurant_IdOrderByCreatedAtDesc(restaurantId, pageable);
    }

    public Page<Expense> getExpensesByDateRange(
            Long restaurantId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return expenseRepository.findByRestaurant_IdAndExpenseDateBetweenOrderByExpenseDateDesc(
                restaurantId, startDate, endDate, pageable);
    }

    public List<Expense> getExpensesByCategory(Long restaurantId, Expense.ExpenseCategory category) {
        return expenseRepository.findByRestaurant_IdAndCategory(restaurantId, category);
    }

    public List<Expense> getExpensesByDateRange(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        return expenseRepository.findByRestaurant_IdAndExpenseDateBetween(restaurantId, startDate, endDate);
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

            Account paymentAccount = accountRepository.findByRestaurant_IdAndCategory(
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
                log.info("Journal entry created for expense {}: amount={}", expense.getExpenseNumber(), expense.getTotalAmount());
            } else {
                log.error("Cannot create journal entry for expense {}: expenseAccount={}, paymentAccount={}. " +
                        "Category: {}, PaymentMethod: {}. Please check chart of accounts for restaurant {}",
                        expense.getExpenseNumber(), expenseAccount != null, paymentAccount != null,
                        expense.getCategory(), expense.getPaymentMethod(), expense.getRestaurant().getId());
            }
        } catch (Exception e) {
            log.error("Failed to create expense journal entry for {}: {}", expense.getExpenseNumber(), e.getMessage(), e);
        }
    }

    private Account findExpenseAccountByCategory(Long restaurantId, Expense.ExpenseCategory category) {
        Account.AccountCategory accountCategory = switch (category) {
            case RENT -> Account.AccountCategory.RENT;
            case UTILITIES -> Account.AccountCategory.UTILITIES;
            case SUPPLIES -> Account.AccountCategory.SUPPLIES;
            case INVENTORY -> Account.AccountCategory.INVENTORY;
            case COST_OF_GOODS_SOLD -> Account.AccountCategory.COGS;
            case MARKETING -> Account.AccountCategory.MARKETING;
            case DELIVERY_COSTS -> Account.AccountCategory.DELIVERY_COSTS;
            default -> Account.AccountCategory.OTHER_EXPENSE;
        };

        return accountRepository.findByRestaurant_IdAndCategory(restaurantId, accountCategory)
                .stream().findFirst().orElse(null);
    }

    /**
     * Create an expense record from a received purchase order
     */
    @Transactional
    public Expense createExpenseFromPurchaseOrder(
            Restaurant restaurant,
            Long purchaseOrderId,
            String poNumber,
            String supplierName,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            LocalDate expenseDate,
            String createdBy
    ) {
        log.info("Creating expense from PO: {} for restaurant: {}", poNumber, restaurant.getId());

        String expenseNumber = generateExpenseNumber(restaurant.getId());

        Expense expense = Expense.builder()
                .restaurant(restaurant)
                .expenseNumber(expenseNumber)
                .expenseDate(expenseDate)
                .category(Expense.ExpenseCategory.INVENTORY)
                .description("Purchase Order: " + poNumber)
                .vendor(supplierName)
                .amount(subtotal)
                .taxAmount(taxAmount != null ? taxAmount : BigDecimal.ZERO)
                .totalAmount(subtotal.add(taxAmount != null ? taxAmount : BigDecimal.ZERO))
                .paymentMethod(Expense.PaymentMethod.BANK_TRANSFER)
                .paymentStatus(Expense.PaymentStatus.UNPAID)
                .referenceNumber(poNumber)
                .purchaseOrderId(purchaseOrderId)
                .createdBy(createdBy)
                .recurring(false)
                .build();

        Expense savedExpense = expenseRepository.save(expense);

        log.info("Expense created from PO: {} -> {}", poNumber, expenseNumber);
        return savedExpense;
    }

    /**
     * Update expense payment status when linked PO payment is recorded
     */
    @Transactional
    public void updateExpensePaymentByPurchaseOrderId(Long purchaseOrderId, LocalDate paymentDate, String recordedBy) {
        expenseRepository.findByPurchaseOrderId(purchaseOrderId).ifPresent(expense -> {
            expense.setPaymentDate(paymentDate);
            expense.setPaymentStatus(Expense.PaymentStatus.PAID);
            expenseRepository.save(expense);

            // Create journal entry for the expense payment
            createExpenseJournalEntry(expense, recordedBy);

            log.info("Expense {} marked as paid for PO ID: {}", expense.getExpenseNumber(), purchaseOrderId);
        });
    }

    private String generateExpenseNumber(Long restaurantId) {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long count = expenseRepository.findByRestaurant_IdOrderByCreatedAtDesc(restaurantId).stream()
                .filter(exp -> exp.getExpenseNumber().startsWith("EXP-" + datePrefix))
                .count();
        return String.format("EXP-%s-%04d", datePrefix, count + 1);
    }
}
