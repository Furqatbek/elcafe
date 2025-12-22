package com.elcafe.modules.financial.controller;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.service.AccountService;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/financial/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * Initialize Chart of Accounts for a restaurant
     * This is useful for existing restaurants that don't have accounts set up
     */
    @PostMapping("/initialize/{restaurantId}")
    public ResponseEntity<ApiResponse<String>> initializeChartOfAccounts(@PathVariable Long restaurantId) {
        log.info("Initializing Chart of Accounts for restaurant: {}", restaurantId);

        try {
            accountService.initializeChartOfAccounts(restaurantId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Chart of Accounts initialized successfully",
                    "Accounts created for restaurant " + restaurantId
            ));
        } catch (Exception e) {
            log.error("Failed to initialize Chart of Accounts for restaurant: {}", restaurantId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Failed to initialize accounts: " + e.getMessage()
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
