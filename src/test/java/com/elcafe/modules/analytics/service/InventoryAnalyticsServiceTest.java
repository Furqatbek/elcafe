package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.InventoryTurnoverDTO;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.inventory.repository.IngredientRepository;
import com.elcafe.modules.inventory.service.BatchConsumptionService;
import com.elcafe.modules.inventory.service.InventoryValuationService;
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
class InventoryAnalyticsServiceTest {

    @Mock private IngredientRepository ingredientRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private InventoryValuationService valuationService;
    @Mock private BatchConsumptionService batchConsumptionService;
    @Mock private ShiftTimeService shiftTimeService;

    @InjectMocks private InventoryAnalyticsService inventoryAnalyticsService;

    @Test
    @DisplayName("getInventoryTurnover — returns turnover data")
    void getInventoryTurnover_returns() {
        when(shiftTimeService.getShiftTimeRangeForPeriod(anyLong(), any(), any()))
                .thenReturn(new ShiftTimeService.ShiftTimeRange(
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(30),
                        OffsetDateTime.now(ZoneOffset.UTC)));
        when(ingredientRepository.findByRestaurantId(1L)).thenReturn(List.of());

        InventoryTurnoverDTO result = inventoryAnalyticsService.getInventoryTurnover(
                LocalDate.now().minusDays(30), LocalDate.now(), 1L);
        assertNotNull(result);
    }
}
