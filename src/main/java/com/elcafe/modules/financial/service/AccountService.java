package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public Account createAccount(Account account) {
        log.info("Creating account: {} for restaurant: {}", account.getName(), account.getRestaurant().getId());

        if (account.getBalance() == null) {
            account.setBalance(java.math.BigDecimal.ZERO);
        }

        return accountRepository.save(account);
    }

    @Transactional
    public Account updateAccount(Long id, Account updatedAccount) {
        log.info("Updating account: {}", id);

        Account existingAccount = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));

        // Only allow updating certain fields, not balance or system status
        existingAccount.setName(updatedAccount.getName());
        existingAccount.setDescription(updatedAccount.getDescription());
        existingAccount.setActive(updatedAccount.getActive());

        if (!existingAccount.getSystemAccount()) {
            existingAccount.setCode(updatedAccount.getCode());
            existingAccount.setType(updatedAccount.getType());
            existingAccount.setCategory(updatedAccount.getCategory());
        }

        return accountRepository.save(existingAccount);
    }

    /**
     * Soft delete an account. Financial records should never be hard deleted for audit compliance.
     *
     * @param id The account ID to delete
     * @param deletedBy Username of the person performing the deletion
     */
    @Transactional
    public void deleteAccount(Long id, String deletedBy) {
        log.info("Soft deleting account: {} by user: {}", id, deletedBy);

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));

        if (account.isDeleted()) {
            throw new RuntimeException("Account has already been deleted");
        }

        if (account.getSystemAccount()) {
            throw new RuntimeException("Cannot delete system account");
        }

        // Use soft delete instead of hard delete for audit compliance
        account.softDelete(deletedBy);
        accountRepository.save(account);
        log.info("Soft deleted account: {} ({}) by user: {}", account.getName(), account.getCode(), deletedBy);
    }

    public Account getAccountById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));
    }

    public List<Account> getAccountsByRestaurant(Long restaurantId) {
        return accountRepository.findByRestaurant_IdAndActiveTrue(restaurantId);
    }

    public List<Account> getAccountsByType(Long restaurantId, Account.AccountType type) {
        return accountRepository.findByRestaurant_IdAndType(restaurantId, type);
    }

    public List<Account> getAccountsByCategory(Long restaurantId, Account.AccountCategory category) {
        return accountRepository.findByRestaurant_IdAndCategory(restaurantId, category);
    }

    public Account getAccountByCode(Long restaurantId, String code) {
        return accountRepository.findByRestaurant_IdAndCode(restaurantId, code)
                .orElseThrow(() -> new RuntimeException("Account not found with code: " + code));
    }

    /**
     * Initialize default chart of accounts for a new restaurant
     */
    @Transactional
    public void initializeChartOfAccounts(Long restaurantId) {
        log.info("Initializing chart of accounts for restaurant: {}", restaurantId);

        // Check if accounts already exist for this restaurant
        if (accountRepository.existsByRestaurant_Id(restaurantId)) {
            log.info("Chart of accounts already exists for restaurant: {}, skipping initialization", restaurantId);
            return;
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + restaurantId));

        // Create default asset accounts
        createDefaultAccount(restaurant, "1000", "Cash", Account.AccountType.ASSET,
                Account.AccountCategory.CASH, Account.NormalBalance.DEBIT, true);
        createDefaultAccount(restaurant, "1100", "Bank Account", Account.AccountType.ASSET,
                Account.AccountCategory.BANK, Account.NormalBalance.DEBIT, true);
        createDefaultAccount(restaurant, "1200", "Inventory", Account.AccountType.ASSET,
                Account.AccountCategory.INVENTORY, Account.NormalBalance.DEBIT, true);

        // Create default liability accounts
        createDefaultAccount(restaurant, "2000", "Accounts Payable", Account.AccountType.LIABILITY,
                Account.AccountCategory.ACCOUNTS_PAYABLE, Account.NormalBalance.CREDIT, true);
        createDefaultAccount(restaurant, "2100", "Taxes Payable", Account.AccountType.LIABILITY,
                Account.AccountCategory.TAXES_PAYABLE, Account.NormalBalance.CREDIT, true);

        // Create default equity accounts
        createDefaultAccount(restaurant, "3000", "Owner's Equity", Account.AccountType.EQUITY,
                Account.AccountCategory.OWNER_EQUITY, Account.NormalBalance.CREDIT, true);
        createDefaultAccount(restaurant, "3100", "Retained Earnings", Account.AccountType.EQUITY,
                Account.AccountCategory.RETAINED_EARNINGS, Account.NormalBalance.CREDIT, true);

        // Create default revenue accounts
        createDefaultAccount(restaurant, "4000", "Sales Revenue", Account.AccountType.REVENUE,
                Account.AccountCategory.SALES, Account.NormalBalance.CREDIT, true);
        createDefaultAccount(restaurant, "4100", "Delivery Fees", Account.AccountType.REVENUE,
                Account.AccountCategory.DELIVERY_FEES, Account.NormalBalance.CREDIT, true);
        createDefaultAccount(restaurant, "4200", "Service Fees", Account.AccountType.REVENUE,
                Account.AccountCategory.SERVICE_FEES, Account.NormalBalance.CREDIT, true);
        createDefaultAccount(restaurant, "4900", "Other Revenue", Account.AccountType.REVENUE,
                Account.AccountCategory.OTHER_REVENUE, Account.NormalBalance.CREDIT, true);

        // Create contra-revenue accounts (reduces revenue - debit normal balance)
        createDefaultAccount(restaurant, "4500", "Sales Discounts", Account.AccountType.REVENUE,
                Account.AccountCategory.SALES_DISCOUNTS, Account.NormalBalance.DEBIT, true);
        createDefaultAccount(restaurant, "4510", "Sales Returns", Account.AccountType.REVENUE,
                Account.AccountCategory.SALES_RETURNS, Account.NormalBalance.DEBIT, true);

        // Create default expense accounts
        createDefaultAccount(restaurant, "5000", "Cost of Goods Sold", Account.AccountType.EXPENSE,
                Account.AccountCategory.COGS, Account.NormalBalance.DEBIT, true);
        createDefaultAccount(restaurant, "5100", "Labor Expenses", Account.AccountType.EXPENSE,
                Account.AccountCategory.LABOR, Account.NormalBalance.DEBIT, true);
        createDefaultAccount(restaurant, "5200", "Rent Expense", Account.AccountType.EXPENSE,
                Account.AccountCategory.RENT, Account.NormalBalance.DEBIT, true);
        createDefaultAccount(restaurant, "5300", "Utilities Expense", Account.AccountType.EXPENSE,
                Account.AccountCategory.UTILITIES, Account.NormalBalance.DEBIT, true);
        createDefaultAccount(restaurant, "5400", "Supplies Expense", Account.AccountType.EXPENSE,
                Account.AccountCategory.SUPPLIES, Account.NormalBalance.DEBIT, true);
        createDefaultAccount(restaurant, "5500", "Delivery Costs", Account.AccountType.EXPENSE,
                Account.AccountCategory.DELIVERY_COSTS, Account.NormalBalance.DEBIT, true);
        createDefaultAccount(restaurant, "5600", "Marketing Expense", Account.AccountType.EXPENSE,
                Account.AccountCategory.MARKETING, Account.NormalBalance.DEBIT, true);
        createDefaultAccount(restaurant, "5900", "Other Expenses", Account.AccountType.EXPENSE,
                Account.AccountCategory.OTHER_EXPENSE, Account.NormalBalance.DEBIT, true);

        log.info("Chart of accounts initialized successfully for restaurant: {}", restaurantId);
    }

    /**
     * Add missing accounts for an existing restaurant.
     * This is used to update restaurants that were created before new account types were added.
     */
    @Transactional
    public void addMissingAccounts(Long restaurantId) {
        log.info("Adding missing accounts for restaurant: {}", restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + restaurantId));

        int added = 0;

        // Check and add missing revenue accounts
        if (accountRepository.findByRestaurant_IdAndCategory(restaurantId, Account.AccountCategory.SERVICE_FEES).isEmpty()) {
            createDefaultAccount(restaurant, "4200", "Service Fees", Account.AccountType.REVENUE,
                    Account.AccountCategory.SERVICE_FEES, Account.NormalBalance.CREDIT, true);
            added++;
        }
        if (accountRepository.findByRestaurant_IdAndCategory(restaurantId, Account.AccountCategory.OTHER_REVENUE).isEmpty()) {
            createDefaultAccount(restaurant, "4900", "Other Revenue", Account.AccountType.REVENUE,
                    Account.AccountCategory.OTHER_REVENUE, Account.NormalBalance.CREDIT, true);
            added++;
        }

        // Check and add missing contra-revenue accounts
        if (accountRepository.findByRestaurant_IdAndCategory(restaurantId, Account.AccountCategory.SALES_DISCOUNTS).isEmpty()) {
            createDefaultAccount(restaurant, "4500", "Sales Discounts", Account.AccountType.REVENUE,
                    Account.AccountCategory.SALES_DISCOUNTS, Account.NormalBalance.DEBIT, true);
            added++;
        }
        if (accountRepository.findByRestaurant_IdAndCategory(restaurantId, Account.AccountCategory.SALES_RETURNS).isEmpty()) {
            createDefaultAccount(restaurant, "4510", "Sales Returns", Account.AccountType.REVENUE,
                    Account.AccountCategory.SALES_RETURNS, Account.NormalBalance.DEBIT, true);
            added++;
        }

        // Check and add missing expense accounts
        if (accountRepository.findByRestaurant_IdAndCategory(restaurantId, Account.AccountCategory.MARKETING).isEmpty()) {
            createDefaultAccount(restaurant, "5600", "Marketing Expense", Account.AccountType.EXPENSE,
                    Account.AccountCategory.MARKETING, Account.NormalBalance.DEBIT, true);
            added++;
        }
        if (accountRepository.findByRestaurant_IdAndCategory(restaurantId, Account.AccountCategory.OTHER_EXPENSE).isEmpty()) {
            createDefaultAccount(restaurant, "5900", "Other Expenses", Account.AccountType.EXPENSE,
                    Account.AccountCategory.OTHER_EXPENSE, Account.NormalBalance.DEBIT, true);
            added++;
        }

        log.info("Added {} missing accounts for restaurant: {}", added, restaurantId);
    }

    private void createDefaultAccount(Restaurant restaurant, String code, String name,
                                     Account.AccountType type, Account.AccountCategory category,
                                     Account.NormalBalance normalBalance, boolean systemAccount) {
        Account account = Account.builder()
                .restaurant(restaurant)
                .code(code)
                .name(name)
                .type(type)
                .category(category)
                .normalBalance(normalBalance)
                .balance(java.math.BigDecimal.ZERO)
                .active(true)
                .systemAccount(systemAccount)
                .build();

        accountRepository.save(account);
    }
}
