package com.elcafe.modules.analytics.service;

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
class FinancialAnalyticsServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private BatchConsumptionService batchConsumptionService;
    @Mock private ShiftTimeService shiftTimeService;
    @InjectMocks private FinancialAnalyticsService financialAnalyticsService;

    private void stubShift() {
        when(shiftTimeService.getShiftTimeRangeForPeriod(anyLong(), any(), any()))
                .thenReturn(new ShiftTimeService.ShiftTimeRange(
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(7), OffsetDateTime.now(ZoneOffset.UTC),
                        LocalTime.of(9, 0), LocalTime.of(23, 0)));
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of());
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    @Test @DisplayName("getDailyRevenue returns") void dailyRevenue() {
        stubShift();
        assertNotNull(financialAnalyticsService.getDailyRevenue(LocalDate.now().minusDays(7), LocalDate.now(), 1L));
    }
    @Test @DisplayName("getSalesPerCategory returns") void salesPerCategory() {
        stubShift();
        when(productRepository.findAll()).thenReturn(List.of());
        assertNotNull(financialAnalyticsService.getSalesPerCategory(LocalDate.now().minusDays(7), LocalDate.now(), 1L));
    }
}
