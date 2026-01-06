package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.entity.Transaction;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.financial.repository.TransactionRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
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
    private final OrderRepository orderRepository;
    private final ShiftTimeService shiftTimeService;

    /**
     * Generate Profit & Loss Statement (Income Statement)
     * Revenue is calculated directly from completed orders for accuracy,
     * as the transaction-based approach requires proper double-entry bookkeeping setup.
     * Uses business hours to determine shift time ranges (handles shifts that cross midnight).
     */
    public ProfitLossReport generateProfitLossReport(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating P&L report for restaurant: {} from {} to {}", restaurantId, startDate, endDate);

        // Get shift-based time range using shared service
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, startDate, endDate);
        log.info("P&L: Using shift time range: {} to {}", shift.start(), shift.end());

        List<Order> orders = orderRepository.findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
                restaurantId, shift.start(), shift.end());

        // Filter to revenue-generating orders:
        // 1. Orders with status in REVENUE_STATUSES (ACCEPTED, PREPARING, READY, etc.)
        // 2. OR orders that are fully paid (regardless of status - handles POS orders)
        // 3. Exclude CANCELLED orders
        List<Order> completedOrders = orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .filter(o -> ShiftTimeService.REVENUE_STATUSES.contains(o.getStatus())
                          || o.isFullyPaid()
                          || o.getPaymentStatus() == PaymentStatus.COMPLETED)
                .collect(Collectors.toList());

        // Calculate revenue breakdown
        BigDecimal salesRevenue = completedOrders.stream()
                .map(Order::getSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal serviceFeeRevenue = completedOrders.stream()
                .map(Order::getServiceFee)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal deliveryFeeRevenue = completedOrders.stream()
                .map(Order::getDeliveryFee)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tipRevenue = completedOrders.stream()
                .map(Order::getTipAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRevenue = completedOrders.stream()
                .map(Order::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("P&L: Found {} orders, {} revenue-counted, sales: {}, serviceFee: {}, deliveryFee: {}, tips: {}, total: {}",
                orders.size(), completedOrders.size(), salesRevenue, serviceFeeRevenue, deliveryFeeRevenue, tipRevenue, totalRevenue);

        // Calculate expenses from expense records (same as DashboardService)
        List<Expense> expenses = expenseRepository.findByRestaurant_IdAndExpenseDateBetween(
                restaurantId, startDate, endDate);

        BigDecimal totalExpenses = expenses.stream()
                .filter(e -> e.getPaymentStatus() == Expense.PaymentStatus.PAID)
                .map(Expense::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group expenses by category
        Map<String, BigDecimal> expensesByCategory = expenses.stream()
                .filter(e -> e.getPaymentStatus() == Expense.PaymentStatus.PAID)
                .filter(e -> e.getTotalAmount() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory() != null ? e.getCategory().name() : "OTHER",
                        Collectors.reducing(BigDecimal.ZERO, Expense::getTotalAmount, BigDecimal::add)
                ));

        log.info("P&L: Total expenses: {}, categories: {}", totalExpenses, expensesByCategory.keySet());

        BigDecimal netIncome = totalRevenue.subtract(totalExpenses);

        return ProfitLossReport.builder()
                .restaurantId(restaurantId)
                .startDate(startDate)
                .endDate(endDate)
                .orderCount(completedOrders.size())
                .salesRevenue(salesRevenue)
                .serviceFeeRevenue(serviceFeeRevenue)
                .deliveryFeeRevenue(deliveryFeeRevenue)
                .tipRevenue(tipRevenue)
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
     * Uses shift-based time ranges to account for transactions during overnight shifts.
     */
    public CashFlowReport generateCashFlowReport(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating cash flow report for restaurant: {} from {} to {}", restaurantId, startDate, endDate);

        // Get shift-based time range to determine if we need to extend the date range
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, startDate, endDate);
        log.info("Cash Flow: Using shift time range: {} to {}", shift.start(), shift.end());

        // Adjust endDate if shift crosses midnight (transactions on next day belong to previous shift)
        LocalDate adjustedEndDate = shift.end().toLocalDate();
        log.info("Cash Flow: Adjusted date range: {} to {}", startDate, adjustedEndDate);

        // Get cash accounts
        List<Account> cashAccounts = accountRepository.findByRestaurant_IdAndCategory(
                restaurantId, Account.AccountCategory.CASH);
        cashAccounts.addAll(accountRepository.findByRestaurant_IdAndCategory(
                restaurantId, Account.AccountCategory.BANK));

        BigDecimal cashInflows = BigDecimal.ZERO;
        BigDecimal cashOutflows = BigDecimal.ZERO;

        for (Account cashAccount : cashAccounts) {
            List<Transaction> transactions = transactionRepository.findByAccount_IdAndTransactionDateBetween(
                    cashAccount.getId(), startDate, adjustedEndDate);

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
     * Uses shift-based time ranges and order-based revenue calculation.
     */
    public CogsReport generateCogsReport(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating COGS report for restaurant: {} from {} to {}", restaurantId, startDate, endDate);

        // Get shift-based time range
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, startDate, endDate);
        log.info("COGS: Using shift time range: {} to {}", shift.start(), shift.end());

        // Get COGS from transaction accounts
        List<Account> cogsAccounts = accountRepository.findByRestaurant_IdAndCategory(
                restaurantId, Account.AccountCategory.COGS);
        BigDecimal totalCogs = calculateAccountsTotal(cogsAccounts, startDate, endDate);

        // Get revenue from orders (consistent with P&L report)
        List<Order> orders = orderRepository.findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
                restaurantId, shift.start(), shift.end());

        BigDecimal totalRevenue = orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .filter(o -> ShiftTimeService.REVENUE_STATUSES.contains(o.getStatus())
                          || o.isFullyPaid()
                          || o.getPaymentStatus() == PaymentStatus.COMPLETED)
                .map(Order::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("COGS: Total COGS: {}, Total Revenue: {}", totalCogs, totalRevenue);

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
        // Order count
        private int orderCount;
        // Revenue breakdown
        private BigDecimal salesRevenue;
        private BigDecimal serviceFeeRevenue;
        private BigDecimal deliveryFeeRevenue;
        private BigDecimal tipRevenue;
        private BigDecimal totalRevenue;
        // Expenses
        private BigDecimal totalExpenses;
        private Map<String, BigDecimal> expensesByCategory;
        // Net income
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
