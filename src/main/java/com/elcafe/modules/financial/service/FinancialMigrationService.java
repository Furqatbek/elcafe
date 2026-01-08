package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.repository.JournalEntryRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Service for migrating historical data to the journal entry system.
 * This is used to sync orders and expenses that were created before
 * the double-entry bookkeeping system was fully integrated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialMigrationService {

    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final RestaurantRepository restaurantRepository;
    private final RevenueService revenueService;
    private final AccountService accountService;
    private final JournalService journalService;

    // Order statuses that indicate revenue should be recorded
    private static final Set<OrderStatus> REVENUE_STATUSES = Set.of(
            OrderStatus.ACCEPTED,
            OrderStatus.PREPARING,
            OrderStatus.READY,
            OrderStatus.PICKED_UP,
            OrderStatus.DELIVERED,
            OrderStatus.SERVED,
            OrderStatus.COMPLETED
    );

    /**
     * Sync all historical data for a restaurant.
     * This includes:
     * 1. Adding any missing accounts to the chart of accounts
     * 2. Creating journal entries for historical orders that don't have them
     * 3. Creating journal entries for historical expenses that don't have them
     */
    @Transactional
    public MigrationResult syncRestaurantFinancialData(Long restaurantId) {
        log.info("Starting financial data sync for restaurant: {}", restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found: " + restaurantId));

        MigrationResult.MigrationResultBuilder result = MigrationResult.builder()
                .restaurantId(restaurantId)
                .restaurantName(restaurant.getName());

        // Step 1: Ensure chart of accounts exists and has all required accounts
        ensureChartOfAccounts(restaurantId);
        result.accountsInitialized(true);

        // Step 2: Sync historical orders
        OrderSyncResult orderResult = syncHistoricalOrders(restaurantId);
        result.ordersProcessed(orderResult.processed)
              .ordersSkipped(orderResult.skipped)
              .ordersFailed(orderResult.failed);

        // Step 3: Sync historical expenses
        ExpenseSyncResult expenseResult = syncHistoricalExpenses(restaurantId);
        result.expensesProcessed(expenseResult.processed)
              .expensesSkipped(expenseResult.skipped)
              .expensesFailed(expenseResult.failed);

        MigrationResult finalResult = result.build();
        log.info("Financial data sync completed for restaurant {}: orders={}/{}, expenses={}/{}",
                restaurantId,
                finalResult.getOrdersProcessed(), orderResult.total,
                finalResult.getExpensesProcessed(), expenseResult.total);

        return finalResult;
    }

    /**
     * Ensure the chart of accounts is initialized and has all required accounts.
     */
    private void ensureChartOfAccounts(Long restaurantId) {
        // Check if chart of accounts exists
        if (!accountRepository.existsByRestaurant_Id(restaurantId)) {
            log.info("Initializing chart of accounts for restaurant: {}", restaurantId);
            accountService.initializeChartOfAccounts(restaurantId);
        } else {
            // Add any missing accounts
            log.info("Adding missing accounts for restaurant: {}", restaurantId);
            accountService.addMissingAccounts(restaurantId);
        }
    }

    /**
     * Sync historical orders that don't have journal entries.
     */
    private OrderSyncResult syncHistoricalOrders(Long restaurantId) {
        log.info("Syncing historical orders for restaurant: {}", restaurantId);

        List<Order> allOrders = orderRepository.findByRestaurant_Id(restaurantId);

        int processed = 0;
        int skipped = 0;
        int failed = 0;

        for (Order order : allOrders) {
            try {
                // Skip cancelled orders
                if (order.getStatus() == OrderStatus.CANCELLED) {
                    skipped++;
                    continue;
                }

                // Skip orders that aren't in a revenue-generating status and aren't paid
                boolean isRevenueStatus = REVENUE_STATUSES.contains(order.getStatus());
                boolean isPaid = order.isFullyPaid() || order.getPaymentStatus() == PaymentStatus.COMPLETED;

                if (!isRevenueStatus && !isPaid) {
                    skipped++;
                    continue;
                }

                // Check if journal entry already exists for this order
                if (journalEntryRepository.existsByReferenceTypeAndReferenceId("ORDER", order.getId())) {
                    skipped++;
                    continue;
                }

                // Record revenue for this order
                revenueService.recordOrderRevenue(order);
                processed++;

                log.debug("Created journal entry for order: {}", order.getOrderNumber());

            } catch (Exception e) {
                log.error("Failed to sync order {}: {}", order.getOrderNumber(), e.getMessage());
                failed++;
            }
        }

        log.info("Order sync complete: processed={}, skipped={}, failed={}, total={}",
                processed, skipped, failed, allOrders.size());

        return new OrderSyncResult(allOrders.size(), processed, skipped, failed);
    }

    /**
     * Sync historical expenses that don't have journal entries.
     */
    private ExpenseSyncResult syncHistoricalExpenses(Long restaurantId) {
        log.info("Syncing historical expenses for restaurant: {}", restaurantId);

        List<Expense> allExpenses = expenseRepository.findByRestaurant_Id(restaurantId);

        int processed = 0;
        int skipped = 0;
        int failed = 0;

        for (Expense expense : allExpenses) {
            try {
                // Skip unpaid expenses (they'll be synced when paid)
                if (expense.getPaymentStatus() != Expense.PaymentStatus.PAID) {
                    skipped++;
                    continue;
                }

                // Check if journal entry already exists for this expense
                if (journalEntryRepository.existsByReferenceTypeAndReferenceId("EXPENSE", expense.getId())) {
                    skipped++;
                    continue;
                }

                // Create journal entry for this expense
                createExpenseJournalEntry(expense, restaurantId);
                processed++;

                log.debug("Created journal entry for expense: {}", expense.getExpenseNumber());

            } catch (Exception e) {
                log.error("Failed to sync expense {}: {}", expense.getExpenseNumber(), e.getMessage());
                failed++;
            }
        }

        log.info("Expense sync complete: processed={}, skipped={}, failed={}, total={}",
                processed, skipped, failed, allExpenses.size());

        return new ExpenseSyncResult(allExpenses.size(), processed, skipped, failed);
    }

    /**
     * Create a journal entry for an expense.
     */
    private void createExpenseJournalEntry(Expense expense, Long restaurantId) {
        // Find the expense account based on category
        Account.AccountCategory accountCategory = mapExpenseCategoryToAccountCategory(expense.getCategory());
        Account expenseAccount = accountRepository.findByRestaurant_IdAndCategory(restaurantId, accountCategory)
                .stream().findFirst().orElse(null);

        // Fall back to OTHER_EXPENSE if specific account not found
        if (expenseAccount == null) {
            expenseAccount = accountRepository.findByRestaurant_IdAndCategory(restaurantId, Account.AccountCategory.OTHER_EXPENSE)
                    .stream().findFirst().orElse(null);
        }

        // Find payment account (cash or bank)
        Account.AccountCategory paymentCategory = expense.getPaymentMethod() == Expense.PaymentMethod.CASH
                ? Account.AccountCategory.CASH
                : Account.AccountCategory.BANK;
        Account paymentAccount = accountRepository.findByRestaurant_IdAndCategory(restaurantId, paymentCategory)
                .stream().findFirst().orElse(null);

        if (expenseAccount == null || paymentAccount == null) {
            throw new RuntimeException("Required accounts not found: expenseAccount=" +
                    (expenseAccount != null) + ", paymentAccount=" + (paymentAccount != null));
        }

        // Create journal entry using JournalService
        // Note: We need to inject JournalService for this
        // For now, we'll use a direct approach through the expense service pattern
        log.info("Creating journal entry for expense {}: debit {} ({}), credit {} ({}), amount={}",
                expense.getExpenseNumber(),
                expenseAccount.getName(), expenseAccount.getCode(),
                paymentAccount.getName(), paymentAccount.getCode(),
                expense.getTotalAmount());

        // The actual journal entry creation is handled by calling the journalService
        // which is injected and used here
        journalServiceCreateEntry(expense, expenseAccount, paymentAccount);
    }

    private void journalServiceCreateEntry(Expense expense, Account expenseAccount, Account paymentAccount) {
        // Debit: Expense Account (increases expense)
        // Credit: Cash/Bank Account (decreases asset)
        journalService.createJournalEntry(
                expense.getRestaurant().getId(),
                expense.getPaymentDate(),
                "Expense: " + expense.getDescription(),
                "EXPENSE",
                expense.getId(),
                expenseAccount.getId(),
                paymentAccount.getId(),
                expense.getTotalAmount(),
                "MIGRATION"
        );
    }

    private Account.AccountCategory mapExpenseCategoryToAccountCategory(Expense.ExpenseCategory category) {
        return switch (category) {
            case RENT -> Account.AccountCategory.RENT;
            case UTILITIES -> Account.AccountCategory.UTILITIES;
            case SUPPLIES -> Account.AccountCategory.SUPPLIES;
            case INVENTORY -> Account.AccountCategory.INVENTORY;
            case COST_OF_GOODS_SOLD -> Account.AccountCategory.COGS;
            case MARKETING -> Account.AccountCategory.MARKETING;
            case DELIVERY_COSTS -> Account.AccountCategory.DELIVERY_COSTS;
            default -> Account.AccountCategory.OTHER_EXPENSE;
        };
    }

    /**
     * Sync all restaurants' financial data.
     */
    @Transactional
    public List<MigrationResult> syncAllRestaurants() {
        log.info("Starting financial data sync for all restaurants");

        List<Restaurant> restaurants = restaurantRepository.findAll();

        return restaurants.stream()
                .map(r -> syncRestaurantFinancialData(r.getId()))
                .toList();
    }

    // Result classes
    private record OrderSyncResult(int total, int processed, int skipped, int failed) {}
    private record ExpenseSyncResult(int total, int processed, int skipped, int failed) {}

    @Data
    @Builder
    public static class MigrationResult {
        private Long restaurantId;
        private String restaurantName;
        private boolean accountsInitialized;
        private int ordersProcessed;
        private int ordersSkipped;
        private int ordersFailed;
        private int expensesProcessed;
        private int expensesSkipped;
        private int expensesFailed;
    }
}
