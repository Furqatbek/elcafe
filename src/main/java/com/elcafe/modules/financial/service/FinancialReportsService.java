package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.entity.Transaction;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.financial.repository.TransactionRepository;
import com.elcafe.modules.order.dto.PnlOrderRow;
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

        // One scalar money row per revenue-qualifying order — the filter (revenue status OR fully paid
        // OR paymentStatus COMPLETED, minus CANCELLED) runs in the DB via the shared fragment that
        // mirrors Order.isFullyPaid() exactly (audit E3); no order/payment entity graphs are loaded.
        List<PnlOrderRow> completedOrders = orderRepository.findPnlOrderRows(
                restaurantId, shift.start(), shift.end(),
                OrderStatus.CANCELLED, ShiftTimeService.REVENUE_STATUSES, PaymentStatus.COMPLETED);

        // Calculate revenue breakdown
        BigDecimal salesRevenue = completedOrders.stream()
                .map(PnlOrderRow::subtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal serviceFeeRevenue = completedOrders.stream()
                .map(PnlOrderRow::serviceFee)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal deliveryFeeRevenue = completedOrders.stream()
                .map(PnlOrderRow::deliveryFee)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tipRevenue = completedOrders.stream()
                .map(PnlOrderRow::tipAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate total discounts (contra-revenue)
        BigDecimal totalDiscounts = completedOrders.stream()
                .map(PnlOrderRow::discount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate discount breakdown by type
        Map<String, BigDecimal> discountsByType = completedOrders.stream()
                .filter(o -> o.discount() != null && o.discount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(
                        o -> o.discountType() != null ? o.discountType() : "UNKNOWN",
                        Collectors.reducing(BigDecimal.ZERO, PnlOrderRow::discount, BigDecimal::add)
                ));

        // Gross revenue (before discounts) = sum of subtotals + fees
        BigDecimal grossRevenue = salesRevenue.add(serviceFeeRevenue).add(deliveryFeeRevenue).add(tipRevenue);

        // Net revenue = gross revenue - discounts (the order total already accounts for discounts)
        BigDecimal totalRevenue = completedOrders.stream()
                .map(PnlOrderRow::total)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("P&L: {} revenue-counted orders, gross: {}, discounts: {}, net: {}",
                completedOrders.size(), grossRevenue, totalDiscounts, totalRevenue);

        // Filter expenses by the SAME timestamp window used for orders,
        // not by calendar date. The old date-only query swept in any
        // expense whose expenseDate happened to land on the same day —
        // including ones recorded hours before the shift opened or
        // after it closed. Telegram shift-closed reports were the most
        // visible victim: an owner closing a 18:00–02:00 shift saw an
        // expense added at 09:00 that morning (a completely different
        // shift) bundled into "today's" totals.
        java.time.LocalDateTime shiftStartLocal = shift.start().toLocalDateTime();
        java.time.LocalDateTime shiftEndLocal = shift.end().toLocalDateTime();
        log.debug("P&L: Expense timestamp window: {} to {}", shiftStartLocal, shiftEndLocal);

        List<Expense> expenses = expenseRepository.findByRestaurant_IdAndCreatedAtBetween(
                restaurantId, shiftStartLocal, shiftEndLocal);

        List<Expense> paidExpenses = expenses.stream()
                .filter(e -> e.getPaymentStatus() == Expense.PaymentStatus.PAID)
                .filter(e -> e.getTotalAmount() != null)
                .collect(Collectors.toList());

        BigDecimal totalExpenses = paidExpenses.stream()
                .map(Expense::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Split out cash that actually left the shift till. Previously
        // this was a proxy ("operator on shift + paymentMethod = CASH")
        // which mis-flagged budget/petty-cash expenses recorded by an
        // on-shift operator. The new model: an expense is drawer-paid
        // only when the operator explicitly says so via paidFromShiftDrawer.
        BigDecimal shiftDrawerExpenses = paidExpenses.stream()
                .filter(e -> Boolean.TRUE.equals(e.getPaidFromShiftDrawer()))
                .map(Expense::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otherExpenses = totalExpenses.subtract(shiftDrawerExpenses);

        // Group expenses by category
        Map<String, BigDecimal> expensesByCategory = expenses.stream()
                .filter(e -> e.getPaymentStatus() == Expense.PaymentStatus.PAID)
                .filter(e -> e.getTotalAmount() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory() != null ? e.getCategory().name() : "OTHER",
                        Collectors.reducing(BigDecimal.ZERO, Expense::getTotalAmount, BigDecimal::add)
                ));

        // Calculate payroll costs (paid within the date range). Payroll
        // queries the paymentDate column which is a LocalDate, so the
        // calendar-day cap remains correct here — we just need the same
        // shift-extended end-date the old expense query computed.
        LocalDate payrollEndDate = shiftEndLocal.toLocalDate().isAfter(endDate)
                ? shiftEndLocal.toLocalDate()
                : endDate;
        BigDecimal totalPayroll = payrollRepository.getTotalPaidPayrollByPaymentDate(
                restaurantId, startDate, payrollEndDate);
        if (totalPayroll == null) totalPayroll = BigDecimal.ZERO;

        log.info("P&L: Total expenses: {}, payroll: {}, categories: {}",
                totalExpenses, totalPayroll, expensesByCategory.keySet());

        BigDecimal totalCosts = totalExpenses.add(totalPayroll);
        BigDecimal netIncome = totalRevenue.subtract(totalCosts);

        // Count orders with discounts
        long discountedOrderCount = completedOrders.stream()
                .filter(o -> o.discount() != null && o.discount().compareTo(BigDecimal.ZERO) > 0)
                .count();

        return ProfitLossReport.builder()
                .restaurantId(restaurantId)
                .startDate(startDate)
                .endDate(endDate)
                .orderCount(completedOrders.size())
                .salesRevenue(salesRevenue)
                .serviceFeeRevenue(serviceFeeRevenue)
                .deliveryFeeRevenue(deliveryFeeRevenue)
                .tipRevenue(tipRevenue)
                .grossRevenue(grossRevenue)
                .totalDiscounts(totalDiscounts)
                .discountsByType(discountsByType)
                .discountedOrderCount((int) discountedOrderCount)
                .totalRevenue(totalRevenue)
                .totalExpenses(totalExpenses)
                .shiftDrawerExpenses(shiftDrawerExpenses)
                .otherExpenses(otherExpenses)
                .totalPayroll(totalPayroll)
                .expensesByCategory(expensesByCategory)
                .netIncome(netIncome)
                .build();
    }

    /**
     * Generate Balance Sheet
     * Uses shift-based time context for consistency with other reports.
     * Note: Balance sheet shows current account balances, not historical point-in-time values.
     */
    public BalanceSheetReport generateBalanceSheet(Long restaurantId, LocalDate asOfDate) {
        log.info("Generating balance sheet for restaurant: {} as of {}", restaurantId, asOfDate);

        // Get shift-based time context for logging consistency
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, asOfDate, asOfDate);
        log.info("Balance Sheet: Using shift time context: {} to {}", shift.start(), shift.end());

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

        // Calculate shift-extended date for transaction queries
        LocalDate shiftEndDate = shift.end().toLocalDate();
        LocalDate txEndDate = shiftEndDate.isAfter(endDate) ? shiftEndDate : endDate;

        // Get COGS from transaction accounts (with shift-extended date range)
        List<Account> cogsAccounts = accountRepository.findByRestaurant_IdAndCategory(
                restaurantId, Account.AccountCategory.COGS);
        BigDecimal totalCogs = calculateAccountsTotal(cogsAccounts, startDate, txEndDate);

        // Get revenue from orders (consistent with P&L report) — aggregated in the DB with the same
        // revenue-qualifying filter
        BigDecimal totalRevenue = orderRepository.sumRevenueTotals(
                restaurantId, shift.start(), shift.end(),
                OrderStatus.CANCELLED, ShiftTimeService.REVENUE_STATUSES, PaymentStatus.COMPLETED)
                .totalRevenue();

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
        private int discountedOrderCount;
        // Revenue breakdown
        private BigDecimal salesRevenue;
        private BigDecimal serviceFeeRevenue;
        private BigDecimal deliveryFeeRevenue;
        private BigDecimal tipRevenue;
        private BigDecimal grossRevenue;          // Revenue before discounts
        // Discount breakdown (contra-revenue)
        private BigDecimal totalDiscounts;
        private Map<String, BigDecimal> discountsByType;  // COUPON, PROMOTION, MANUAL, HAPPY_HOUR
        // Net revenue after discounts
        private BigDecimal totalRevenue;
        // Expenses
        private BigDecimal totalExpenses;
        private BigDecimal shiftDrawerExpenses; // PAID + cash + tied to a shift
        private BigDecimal otherExpenses;       // everything else in totalExpenses
        private BigDecimal totalPayroll;
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
