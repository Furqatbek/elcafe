package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.dto.DashboardResponse;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.repository.OrderRepository;
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
class DashboardServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private PayrollEntryRepository payrollRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ShiftTimeService shiftTimeService;
    @InjectMocks private DashboardService dashboardService;

    private void stubShift() {
        ShiftTimeService.ShiftTimeRange range = new ShiftTimeService.ShiftTimeRange(
                OffsetDateTime.of(2026, 3, 1, 9, 0, 0, 0, ZoneOffset.of("+05:00")),
                OffsetDateTime.of(2026, 3, 1, 23, 0, 0, 0, ZoneOffset.of("+05:00")),
                LocalTime.of(9, 0), LocalTime.of(23, 0));
        when(shiftTimeService.getShiftTimeRange(anyLong(), any(LocalDate.class))).thenReturn(range);
        when(shiftTimeService.getShiftTimeRangeForPeriod(anyLong(), any(), any())).thenReturn(range);
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of());
        when(shiftTimeService.getCurrentBusinessDay(anyLong())).thenReturn(LocalDate.now());
    }

    private void stubEmpty() {
        stubShift();
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(expenseRepository.findByRestaurant_IdAndExpenseDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(payrollRepository.findByRestaurant_IdAndPayPeriodEndBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    @Test @DisplayName("getTodaySummary returns response") void today() {
        stubEmpty();
        DashboardResponse result = dashboardService.getTodaySummary(1L);
        assertNotNull(result);
    }
    @Test @DisplayName("getWeekSummary returns response") void week() {
        stubEmpty();
        DashboardResponse result = dashboardService.getWeekSummary(1L);
        assertNotNull(result);
    }
    @Test @DisplayName("getMonthSummary returns response") void month() {
        stubEmpty();
        DashboardResponse result = dashboardService.getMonthSummary(1L);
        assertNotNull(result);
    }
}
