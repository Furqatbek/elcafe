package com.elcafe.modules.financial.service;

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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
    }

    @Test @DisplayName("P&L report") void profitLoss() {
        assertNotNull(reportsService.generateProfitLossReport(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
    }
    @Test @DisplayName("Balance sheet") void balanceSheet() {
        assertNotNull(reportsService.generateBalanceSheet(1L, LocalDate.now()));
    }
}
