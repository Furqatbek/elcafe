package com.elcafe.modules.analytics.service;

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
class OperationalAnalyticsServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private KitchenOrderRepository kitchenOrderRepository;
    @Mock private ShiftTimeService shiftTimeService;
    @Mock private RestaurantTableRepository restaurantTableRepository;
    @InjectMocks private OperationalAnalyticsService operationalAnalyticsService;

    private void stubShift() {
        when(shiftTimeService.getShiftTimeRangeForPeriod(anyLong(), any(), any()))
                .thenReturn(new ShiftTimeService.ShiftTimeRange(
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(7), OffsetDateTime.now(ZoneOffset.UTC),
                        LocalTime.of(9, 0), LocalTime.of(23, 0)));
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of());
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    @Test @DisplayName("getSalesPerHour returns") void salesPerHour() {
        stubShift();
        assertNotNull(operationalAnalyticsService.getSalesPerHour(LocalDate.now().minusDays(7), LocalDate.now(), 1L));
    }
    @Test @DisplayName("getPeakHours returns") void peakHours() {
        stubShift();
        assertNotNull(operationalAnalyticsService.getPeakHours(LocalDate.now().minusDays(7), LocalDate.now(), 1L));
    }
}
