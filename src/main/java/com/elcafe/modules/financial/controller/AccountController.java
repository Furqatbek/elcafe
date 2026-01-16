package com.elcafe.modules.financial.controller;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.service.AccountService;
import com.elcafe.modules.financial.service.FinancialMigrationService;
import com.elcafe.modules.financial.service.RevenueService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.financial.repository.JournalEntryRepository;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/financial/accounts")
@RequiredArgsConstructor
@Tag(name = "Financial Accounts", description = "Chart of Accounts management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
public class AccountController {

    private final AccountService accountService;
    private final RevenueService revenueService;
    private final FinancialMigrationService migrationService;
    private final OrderRepository orderRepository;
    private final JournalEntryRepository journalEntryRepository;

    // Only backfill statuses for orders that are truly completed and paid
    // In-progress orders (PENDING, NEW, PLACED, ACCEPTING, PREPARING, etc.) should NOT be backfilled
    // as they haven't been paid yet and will receive real payments later
    private static final List<OrderStatus> BACKFILL_STATUSES = Arrays.asList(
            OrderStatus.READY,
            OrderStatus.PICKED_UP,
            OrderStatus.DELIVERED,
            OrderStatus.COMPLETED
    );

    /**
     * Initialize Chart of Accounts for a restaurant
     * This is useful for existing restaurants that don't have accounts set up
     */
    @PostMapping("/initialize/{restaurantId}")
    public ResponseEntity<ApiResponse<String>> initializeChartOfAccounts(@PathVariable Long restaurantId) {
        log.info("Initializing Chart of Accounts for restaurant: {}", restaurantId);

        try {
            accountService.initializeChartOfAccounts(restaurantId);

            // Also backfill historical orders
            int backfilledCount = backfillHistoricalOrders(restaurantId);

            return ResponseEntity.ok(ApiResponse.success(
                    "Chart of Accounts initialized successfully",
                    "Accounts created for restaurant " + restaurantId + ". Backfilled " + backfilledCount + " historical orders."
            ));
        } catch (Exception e) {
            log.error("Failed to initialize Chart of Accounts for restaurant: {}", restaurantId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Failed to initialize accounts: " + e.getMessage()
            ));
        }
    }

    /**
     * Backfill historical orders to create financial transactions
     * This is useful for orders that were completed before the financial system was set up
     */
    @PostMapping("/backfill/{restaurantId}")
    public ResponseEntity<ApiResponse<String>> backfillHistoricalOrdersEndpoint(@PathVariable Long restaurantId) {
        log.info("Backfilling historical orders for restaurant: {}", restaurantId);

        try {
            int count = backfillHistoricalOrders(restaurantId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Historical orders backfilled successfully",
                    "Processed " + count + " orders for restaurant " + restaurantId
            ));
        } catch (Exception e) {
            log.error("Failed to backfill historical orders for restaurant: {}", restaurantId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Failed to backfill orders: " + e.getMessage()
            ));
        }
    }

    /**
     * Helper method to backfill historical orders
     */
    private int backfillHistoricalOrders(Long restaurantId) {
        // Collect orders from all relevant statuses
        List<Order> allOrders = new ArrayList<>();
        for (OrderStatus status : BACKFILL_STATUSES) {
            try {
                List<Order> orders = orderRepository.findByRestaurant_IdAndStatus(restaurantId, status);
                allOrders.addAll(orders);
            } catch (Exception e) {
                log.warn("Failed to fetch orders with status {}: {}", status, e.getMessage());
            }
        }

        log.info("Found {} orders to potentially backfill for restaurant: {}", allOrders.size(), restaurantId);

        int processedCount = 0;
        for (Order order : allOrders) {
            // Check if journal entry already exists for this order
            boolean hasJournalEntry = journalEntryRepository.existsByReferenceTypeAndReferenceId(
                    "ORDER", order.getId());

            if (!hasJournalEntry && order.getTotal() != null && order.getTotal().compareTo(java.math.BigDecimal.ZERO) > 0) {
                try {
                    revenueService.recordOrderRevenue(order);
                    processedCount++;
                    log.debug("Backfilled revenue for order: {} (status: {})", order.getId(), order.getStatus());
                } catch (Exception e) {
                    log.warn("Failed to backfill order {}: {}", order.getId(), e.getMessage());
                }
            }
        }

        log.info("Backfilled {} historical orders for restaurant: {}", processedCount, restaurantId);
        return processedCount;
    }

    /**
     * Full financial data sync for a restaurant.
     * This includes:
     * - Adding missing accounts
     * - Syncing historical orders to journal entries
     * - Syncing historical expenses to journal entries
     */
    @PostMapping("/sync/{restaurantId}")
    public ResponseEntity<ApiResponse<FinancialMigrationService.MigrationResult>> syncFinancialData(
            @PathVariable Long restaurantId) {
        log.info("Starting full financial data sync for restaurant: {}", restaurantId);

        try {
            FinancialMigrationService.MigrationResult result = migrationService.syncRestaurantFinancialData(restaurantId);

            return ResponseEntity.ok(ApiResponse.success(
                    "Financial data synchronized successfully",
                    result
            ));
        } catch (Exception e) {
            log.error("Failed to sync financial data for restaurant: {}", restaurantId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Failed to sync financial data: " + e.getMessage()
            ));
        }
    }

    /**
     * Full financial data sync for all restaurants.
     * WARNING: This can be a heavy operation for large deployments.
     */
    @PostMapping("/sync-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<FinancialMigrationService.MigrationResult>>> syncAllFinancialData() {
        log.info("Starting full financial data sync for ALL restaurants");

        try {
            List<FinancialMigrationService.MigrationResult> results = migrationService.syncAllRestaurants();

            return ResponseEntity.ok(ApiResponse.success(
                    "Financial data synchronized for all restaurants",
                    results
            ));
        } catch (Exception e) {
            log.error("Failed to sync financial data for all restaurants", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Failed to sync financial data: " + e.getMessage()
            ));
        }
    }

    /**
     * Add missing accounts to an existing restaurant's chart of accounts.
     * This adds new account types that were introduced after the restaurant was created.
     */
    @PostMapping("/add-missing/{restaurantId}")
    public ResponseEntity<ApiResponse<String>> addMissingAccounts(@PathVariable Long restaurantId) {
        log.info("Adding missing accounts for restaurant: {}", restaurantId);

        try {
            accountService.addMissingAccounts(restaurantId);

            return ResponseEntity.ok(ApiResponse.success(
                    "Missing accounts added successfully",
                    "Restaurant " + restaurantId + " chart of accounts updated"
            ));
        } catch (Exception e) {
            log.error("Failed to add missing accounts for restaurant: {}", restaurantId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Failed to add missing accounts: " + e.getMessage()
            ));
        }
    }

    /**
     * Get all accounts for a restaurant
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Account>>> getAccounts(@RequestParam Long restaurantId) {
        log.info("Fetching accounts for restaurant: {}", restaurantId);

        List<Account> accounts = accountService.getAccountsByRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Accounts fetched successfully", accounts));
    }

    /**
     * Get accounts by type
     */
    @GetMapping("/by-type")
    public ResponseEntity<ApiResponse<List<Account>>> getAccountsByType(
            @RequestParam Long restaurantId,
            @RequestParam Account.AccountType type) {
        log.info("Fetching {} accounts for restaurant: {}", type, restaurantId);

        List<Account> accounts = accountService.getAccountsByType(restaurantId, type);
        return ResponseEntity.ok(ApiResponse.success("Accounts fetched successfully", accounts));
    }

    /**
     * Get accounts by category
     */
    @GetMapping("/by-category")
    public ResponseEntity<ApiResponse<List<Account>>> getAccountsByCategory(
            @RequestParam Long restaurantId,
            @RequestParam Account.AccountCategory category) {
        log.info("Fetching {} accounts for restaurant: {}", category, restaurantId);

        List<Account> accounts = accountService.getAccountsByCategory(restaurantId, category);
        return ResponseEntity.ok(ApiResponse.success("Accounts fetched successfully", accounts));
    }

    /**
     * Get a specific account by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Account>> getAccountById(@PathVariable Long id) {
        log.info("Fetching account: {}", id);

        Account account = accountService.getAccountById(id);
        return ResponseEntity.ok(ApiResponse.success("Account fetched successfully", account));
    }

    /**
     * Create a new account
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Account>> createAccount(@RequestBody Account account) {
        log.info("Creating new account: {}", account.getName());

        Account createdAccount = accountService.createAccount(account);
        return ResponseEntity.ok(ApiResponse.success("Account created successfully", createdAccount));
    }

    /**
     * Update an existing account
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Account>> updateAccount(
            @PathVariable Long id,
            @RequestBody Account account) {
        log.info("Updating account: {}", id);

        Account updatedAccount = accountService.updateAccount(id, account);
        return ResponseEntity.ok(ApiResponse.success("Account updated successfully", updatedAccount));
    }

    /**
     * Delete an account
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable Long id) {
        log.info("Deleting account: {}", id);

        accountService.deleteAccount(id);
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully", null));
    }
}
