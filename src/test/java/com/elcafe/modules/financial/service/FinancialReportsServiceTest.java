package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.Transaction;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialReportsServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private PayrollEntryRepository payrollRepository;
    @Mock private AccountService accountService;
    @Mock private OrderRepository orderRepository;
    @Mock private ShiftTimeService shiftTimeService;

    @InjectMocks private FinancialReportsService reportsService;

    @BeforeEach
    void setUp() {
        ShiftTimeService.ShiftTimeRange range = new ShiftTimeService.ShiftTimeRange(
                OffsetDateTime.of(2026, 3, 1, 9, 0, 0, 0, ZoneOffset.of("+05:00")),
                OffsetDateTime.of(2026, 3, 31, 23, 0, 0, 0, ZoneOffset.of("+05:00"))
        );
        when(shiftTimeService.getShiftTimeRangeForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(range);
    }

    @Test
    @DisplayName("generateProfitLossReport — returns report")
    void profitLoss_returnsReport() {
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(expenseRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(payrollRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of());

        var result = reportsService.generateProfitLossReport(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertNotNull(result);
    }

    @Test
    @DisplayName("generateBalanceSheet — returns report")
    void balanceSheet_returnsReport() {
        when(accountRepository.findByRestaurantId(1L)).thenReturn(List.of());

        var result = reportsService.generateBalanceSheet(1L, LocalDate.now());
        assertNotNull(result);
    }

    @Test
    @DisplayName("generateCashFlowReport — returns report")
    void cashFlow_returnsReport() {
        when(transactionRepository.findByAccountRestaurantIdAndTransactionDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of());

        var result = reportsService.generateCashFlowReport(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertNotNull(result);
    }

    @Test
    @DisplayName("generateCogsReport — returns report")
    void cogsReport_returnsReport() {
        when(transactionRepository.findByAccountRestaurantIdAndTransactionDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(accountRepository.findByRestaurantIdAndAccountType(anyLong(), any())).thenReturn(java.util.Optional.empty());

        var result = reportsService.generateCogsReport(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertNotNull(result);
    }

    @Test
    @DisplayName("P&L report — zero revenue and expenses produces zero profit")
    void profitLoss_zeroRevenue_zeroProfit() {
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(expenseRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any())).thenReturn(null);
        when(payrollRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any())).thenReturn(null);
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of());

        var result = reportsService.generateProfitLossReport(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getNetProfit()));
    }
}
