package com.elcafe.modules.promotion.service;

import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import com.elcafe.modules.promotion.repository.PromotionUsageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionAnalyticsServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PromotionUsageRepository promotionUsageRepository;

    @InjectMocks private PromotionAnalyticsService promotionAnalyticsService;

    @Test
    @DisplayName("getDiscountAnalytics — returns analytics")
    void getDiscountAnalytics_returns() {
        when(orderRepository.findByRestaurantIdAndCreatedAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());

        var result = promotionAnalyticsService.getDiscountAnalytics(1L,
                LocalDate.now().minusDays(30), LocalDate.now());
        assertNotNull(result);
    }

    @Test
    @DisplayName("getAllPromotionsPerformance — returns list")
    void getAllPromotionsPerformance_returns() {
        when(promotionRepository.findByRestaurantId(1L)).thenReturn(List.of());

        var result = promotionAnalyticsService.getAllPromotionsPerformance(1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getDiscountTrends — returns daily trends")
    void getDiscountTrends_returns() {
        when(orderRepository.findByRestaurantIdAndCreatedAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());

        var result = promotionAnalyticsService.getDiscountTrends(1L,
                LocalDate.now().minusDays(7), LocalDate.now());
        assertNotNull(result);
    }
}
