package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.Transaction;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.financial.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportsService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ExpenseRepository expenseRepository;
    private final PayrollEntryRepository payrollRepository;
    private final AccountService accountService;

    /**
     * Generate Profit & Loss Statement (Income Statement)
     */
    public ProfitLossReport generateProfitLossReport(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating P&L report for restaurant: {} from {} to {}", restaurantId, startDate, endDate);

        // Check if accounts exist, if not initialize them
        List<Account> allAccounts = accountRepository.findByRestaurant_IdAndActiveTrue(restaurantId);
        if (allAccounts.isEmpty()) {
            log.warn("No financial accounts found for restaurant {}. Initializing chart of accounts...", restaurantId);
            try {
                accountService.initializeChartOfAccounts(restaurantId);
                log.info("Chart of accounts initialized for restaurant {}", restaurantId);
            } catch (Exception e) {
                log.error("Failed to initialize chart of accounts for restaurant {}: {}", restaurantId, e.getMessage());
            }
        }

        // Get all revenue accounts
        List<Account> revenueAccounts = accountRepository.findByRestaurant_IdAndType(
                restaurantId, Account.AccountType.REVENUE);
        log.debug("Found {} revenue accounts for restaurant {}", revenueAccounts.size(), restaurantId);

        BigDecimal totalRevenue = calculateAccountsTotal(revenueAccounts, startDate, endDate);
        log.debug("Total revenue calculated: {}", totalRevenue);

        // Get all expense accounts
        List<Account> expenseAccounts = accountRepository.findByRestaurant_IdAndType(
                restaurantId, Account.AccountType.EXPENSE);
        log.debug("Found {} expense accounts for restaurant {}", expenseAccounts.size(), restaurantId);

        Map<String, BigDecimal> expensesByCategory = new HashMap<>();
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (Account account : expenseAccounts) {
            BigDecimal amount = calculateAccountTotal(account.getId(), startDate, endDate);
            expensesByCategory.put(account.getCategory().toString(), amount);
            totalExpenses = totalExpenses.add(amount);
        }

        BigDecimal netIncome = totalRevenue.subtract(totalExpenses);

        return ProfitLossReport.builder()
                .restaurantId(restaurantId)
                .startDate(startDate)
                .endDate(endDate)
                .totalRevenue(totalRevenue)
                .totalExpenses(totalExpenses)
                .expensesByCategory(expensesByCategory)
                .netIncome(netIncome)
                .build();
    }

    /**
     * Generate Balance Sheet
     */
    public BalanceSheetReport generateBalanceSheet(Long restaurantId, LocalDate asOfDate) {
        log.info("Generating balance sheet for restaurant: {} as of {}", restaurantId, asOfDate);

        // Assets
        List<Account> assetAccounts = accountRepository.findByRestaurant_IdAndType(
                restaurantId, Account.AccountType.ASSET);
        BigDecimal totalAssets = assetAccounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> assetsByCategory = assetAccounts.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCategory().toString(),
                        Collectors.reducing(BigDecimal.ZERO, Account::getBalance, BigDecimal::add)
                ));

        // Liabilities
        List<Account> liabilityAccounts = accountRepository.findByRestaurant_IdAndType(
                restaurantId, Account.AccountType.LIABILITY);
        BigDecimal totalLiabilities = liabilityAccounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> liabilitiesByCategory = liabilityAccounts.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCategory().toString(),
                        Collectors.reducing(BigDecimal.ZERO, Account::getBalance, BigDecimal::add)
                ));

        // Equity
        List<Account> equityAccounts = accountRepository.findByRestaurant_IdAndType(
                restaurantId, Account.AccountType.EQUITY);
        BigDecimal totalEquity = equityAccounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BalanceSheetReport.builder()
                .restaurantId(restaurantId)
                .asOfDate(asOfDate)
                .totalAssets(totalAssets)
                .assetsByCategory(assetsByCategory)
                .totalLiabilities(totalLiabilities)
                .liabilitiesByCategory(liabilitiesByCategory)
                .totalEquity(totalEquity)
                .build();
    }

    /**
     * Generate Cash Flow Statement
     */
    public CashFlowReport generateCashFlowReport(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating cash flow report for restaurant: {} from {} to {}", restaurantId, startDate, endDate);

        // Get cash accounts
        List<Account> cashAccounts = accountRepository.findByRestaurant_IdAndCategory(
                restaurantId, Account.AccountCategory.CASH);
        cashAccounts.addAll(accountRepository.findByRestaurant_IdAndCategory(
                restaurantId, Account.AccountCategory.BANK));

        BigDecimal cashInflows = BigDecimal.ZERO;
        BigDecimal cashOutflows = BigDecimal.ZERO;

        for (Account cashAccount : cashAccounts) {
            List<Transaction> transactions = transactionRepository.findByAccount_IdAndTransactionDateBetween(
                    cashAccount.getId(), startDate, endDate);

            for (Transaction tx : transactions) {
                if (cashAccount.getNormalBalance() == Account.NormalBalance.DEBIT) {
                    if (tx.getType() == Transaction.TransactionType.DEBIT) {
                        cashInflows = cashInflows.add(tx.getAmount());
                    } else {
                        cashOutflows = cashOutflows.add(tx.getAmount());
                    }
                } else {
                    if (tx.getType() == Transaction.TransactionType.CREDIT) {
                        cashInflows = cashInflows.add(tx.getAmount());
                    } else {
                        cashOutflows = cashOutflows.add(tx.getAmount());
                    }
                }
            }
        }

        BigDecimal netCashFlow = cashInflows.subtract(cashOutflows);

        return CashFlowReport.builder()
                .restaurantId(restaurantId)
                .startDate(startDate)
                .endDate(endDate)
                .cashInflows(cashInflows)
                .cashOutflows(cashOutflows)
                .netCashFlow(netCashFlow)
                .build();
    }

    /**
     * Generate COGS (Cost of Goods Sold) Report
     */
    public CogsReport generateCogsReport(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating COGS report for restaurant: {} from {} to {}", restaurantId, startDate, endDate);

        List<Account> cogsAccounts = accountRepository.findByRestaurant_IdAndCategory(
                restaurantId, Account.AccountCategory.COGS);

        BigDecimal totalCogs = calculateAccountsTotal(cogsAccounts, startDate, endDate);

        // Get revenue for calculating COGS percentage
        List<Account> revenueAccounts = accountRepository.findByRestaurant_IdAndType(
                restaurantId, Account.AccountType.REVENUE);
        BigDecimal totalRevenue = calculateAccountsTotal(revenueAccounts, startDate, endDate);

        BigDecimal cogsPercentage = BigDecimal.ZERO;
        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            cogsPercentage = totalCogs.divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        return CogsReport.builder()
                .restaurantId(restaurantId)
                .startDate(startDate)
                .endDate(endDate)
                .totalCogs(totalCogs)
                .totalRevenue(totalRevenue)
                .cogsPercentage(cogsPercentage)
                .build();
    }

    private BigDecimal calculateAccountsTotal(List<Account> accounts, LocalDate startDate, LocalDate endDate) {
        BigDecimal total = BigDecimal.ZERO;
        for (Account account : accounts) {
            total = total.add(calculateAccountTotal(account.getId(), startDate, endDate));
        }
        return total;
    }

    private BigDecimal calculateAccountTotal(Long accountId, LocalDate startDate, LocalDate endDate) {
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            log.warn("Account not found: {}", accountId);
            return BigDecimal.ZERO;
        }

        List<Transaction> transactions = transactionRepository.findByAccount_IdAndTransactionDateBetween(
                accountId, startDate, endDate);

        log.debug("Account {} ({}): found {} transactions between {} and {}",
            account.getName(), account.getCode(), transactions.size(), startDate, endDate);

        BigDecimal total = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            if (account.getNormalBalance() == Account.NormalBalance.DEBIT) {
                // For debit-normal accounts (Expenses, Assets): debits add, credits subtract
                if (tx.getType() == Transaction.TransactionType.DEBIT) {
                    total = total.add(tx.getAmount());
                } else {
                    total = total.subtract(tx.getAmount());
                }
            } else {
                // For credit-normal accounts (Revenue, Liabilities, Equity): credits add, debits subtract
                if (tx.getType() == Transaction.TransactionType.CREDIT) {
                    total = total.add(tx.getAmount());
                } else {
                    total = total.subtract(tx.getAmount());
                }
            }
        }

        return total;
    }

    @Data
    @Builder
    public static class ProfitLossReport {
        private Long restaurantId;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal totalRevenue;
        private BigDecimal totalExpenses;
        private Map<String, BigDecimal> expensesByCategory;
        private BigDecimal netIncome;
    }

    @Data
    @Builder
    public static class BalanceSheetReport {
        private Long restaurantId;
        private LocalDate asOfDate;
        private BigDecimal totalAssets;
        private Map<String, BigDecimal> assetsByCategory;
        private BigDecimal totalLiabilities;
        private Map<String, BigDecimal> liabilitiesByCategory;
        private BigDecimal totalEquity;
    }

    @Data
    @Builder
    public static class CashFlowReport {
        private Long restaurantId;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal cashInflows;
        private BigDecimal cashOutflows;
        private BigDecimal netCashFlow;
    }

    @Data
    @Builder
    public static class CogsReport {
        private Long restaurantId;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal totalCogs;
        private BigDecimal totalRevenue;
        private BigDecimal cogsPercentage;
    }
}
