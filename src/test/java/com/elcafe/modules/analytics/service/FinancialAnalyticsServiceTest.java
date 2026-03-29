package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.DailyRevenueDTO;
import com.elcafe.modules.analytics.dto.SalesPerCategoryDTO;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.inventory.service.BatchConsumptionService;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialAnalyticsServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private BatchConsumptionService batchConsumptionService;
    @Mock private ShiftTimeService shiftTimeService;

    @InjectMocks private FinancialAnalyticsService financialAnalyticsService;

    private void stubShiftTime() {
        when(shiftTimeService.getShiftTimeRangeForPeriod(anyLong(), any(), any()))
                .thenReturn(new ShiftTimeService.ShiftTimeRange(
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(7),
                        OffsetDateTime.now(ZoneOffset.UTC)));
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of());
    }

    @Test
    @DisplayName("getDailyRevenue — returns daily breakdown")
    void getDailyRevenue_returns() {
        stubShiftTime();
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());

        List<DailyRevenueDTO> result = financialAnalyticsService.getDailyRevenue(
                LocalDate.now().minusDays(7), LocalDate.now(), 1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getSalesPerCategory — returns category breakdown")
    void getSalesPerCategory_returns() {
        stubShiftTime();
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(List.of());

        List<SalesPerCategoryDTO> result = financialAnalyticsService.getSalesPerCategory(
                LocalDate.now().minusDays(7), LocalDate.now(), 1L);
        assertNotNull(result);
    }
}
