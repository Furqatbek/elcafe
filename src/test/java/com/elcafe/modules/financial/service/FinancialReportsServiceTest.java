package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.financial.repository.TransactionRepository;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialReportsServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private PayrollEntryRepository payrollRepository;
    @Mock private AccountService accountService;
    @Mock private OrderRepository orderRepository;
    @Mock private ShiftTimeService shiftTimeService;
    @Mock private CogsService cogsService;
    @InjectMocks private FinancialReportsService reportsService;

    @BeforeEach
    void setUp() {
        ShiftTimeService.ShiftTimeRange range = new ShiftTimeService.ShiftTimeRange(
                OffsetDateTime.of(2026, 3, 1, 9, 0, 0, 0, ZoneOffset.of("+05:00")),
                OffsetDateTime.of(2026, 3, 31, 23, 0, 0, 0, ZoneOffset.of("+05:00")),
                LocalTime.of(9, 0), LocalTime.of(23, 0));
        when(shiftTimeService.getShiftTimeRangeForPeriod(anyLong(), any(), any())).thenReturn(range);
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of());
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(expenseRepository.findByRestaurant_IdAndExpenseDateBetween(anyLong(), any(), any())).thenReturn(List.of());
        when(payrollRepository.findByRestaurant_IdAndPayPeriodEndBetween(anyLong(), any(), any())).thenReturn(List.of());
        when(accountRepository.findByRestaurant_IdAndActiveTrue(anyLong())).thenReturn(List.of());
        when(cogsService.calculateCogs(any())).thenReturn(BigDecimal.ZERO);
    }

    @Test @DisplayName("P&L report") void profitLoss() {
        assertNotNull(reportsService.generateProfitLossReport(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
    }

    @Test @DisplayName("P&L charges COGS on top of operating expenses")
    void profitLossSubtractsCogs() {
        Expense supplies = new Expense();
        supplies.setCategory(Expense.ExpenseCategory.SUPPLIES);
        supplies.setPaymentStatus(Expense.PaymentStatus.PAID);
        supplies.setTotalAmount(new BigDecimal("200000"));
        when(expenseRepository.findByRestaurant_IdAndCreatedAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of(supplies));
        when(cogsService.calculateCogs(any())).thenReturn(new BigDecimal("450000"));

        FinancialReportsService.ProfitLossReport report =
                reportsService.generateProfitLossReport(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertEquals(0, new BigDecimal("450000").compareTo(report.getTotalCogs()),
                "COGS is reported as its own line");
        assertEquals(0, new BigDecimal("200000").compareTo(report.getTotalExpenses()),
                "operating expenses stay separate from COGS");
        assertEquals(0, new BigDecimal("-650000").compareTo(report.getNetIncome()),
                "netIncome = 0 revenue - 450k COGS - 200k expenses");
    }
    @Test @DisplayName("Balance sheet") void balanceSheet() {
        assertNotNull(reportsService.generateBalanceSheet(1L, LocalDate.now()));
    }
}
