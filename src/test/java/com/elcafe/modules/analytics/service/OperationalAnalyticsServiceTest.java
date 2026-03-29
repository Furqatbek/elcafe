package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.PeakHoursDTO;
import com.elcafe.modules.analytics.dto.SalesPerHourDTO;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.kitchen.repository.KitchenOrderRepository;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
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
class OperationalAnalyticsServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private KitchenOrderRepository kitchenOrderRepository;
    @Mock private ShiftTimeService shiftTimeService;
    @Mock private RestaurantTableRepository restaurantTableRepository;

    @InjectMocks private OperationalAnalyticsService operationalAnalyticsService;

    private void stubShiftTime() {
        when(shiftTimeService.getShiftTimeRangeForPeriod(anyLong(), any(), any()))
                .thenReturn(new ShiftTimeService.ShiftTimeRange(
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(7),
                        OffsetDateTime.now(ZoneOffset.UTC)));
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of());
    }

    @Test
    @DisplayName("getSalesPerHour — returns hourly breakdown")
    void getSalesPerHour_returns() {
        stubShiftTime();
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());

        List<SalesPerHourDTO> result = operationalAnalyticsService.getSalesPerHour(
                LocalDate.now().minusDays(7), LocalDate.now(), 1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPeakHours — returns peak hours analysis")
    void getPeakHours_returns() {
        stubShiftTime();
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());

        PeakHoursDTO result = operationalAnalyticsService.getPeakHours(
                LocalDate.now().minusDays(7), LocalDate.now(), 1L);
        assertNotNull(result);
    }
}
