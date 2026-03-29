package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsSummaryServiceTest {

    @Mock private FinancialAnalyticsService financialAnalyticsService;
    @Mock private OperationalAnalyticsService operationalAnalyticsService;
    @Mock private CustomerAnalyticsService customerAnalyticsService;
    @Mock private InventoryAnalyticsService inventoryAnalyticsService;

    @InjectMocks private AnalyticsSummaryService summaryService;

    @Test
    @DisplayName("getAnalyticsSummary — aggregates all analytics")
    void getSummary_aggregatesAll() {
        when(financialAnalyticsService.getDailyRevenue(any(), any(), anyLong())).thenReturn(List.of());
        when(financialAnalyticsService.getCOGSAnalytics(any(), any(), anyLong()))
                .thenReturn(COGSAnalyticsDTO.builder().totalCOGS(BigDecimal.ZERO).build());
        when(financialAnalyticsService.getProfitabilityAnalytics(any(), any(), anyLong(), any(), any()))
                .thenReturn(ProfitabilityAnalyticsDTO.builder()
                        .grossProfit(BigDecimal.ZERO).grossProfitMargin(BigDecimal.ZERO)
                        .netProfit(BigDecimal.ZERO).netProfitMargin(BigDecimal.ZERO).build());
        when(operationalAnalyticsService.getOrderTimingAnalytics(any(), any(), anyLong()))
                .thenReturn(OrderTimingAnalyticsDTO.builder()
                        .averagePreparationTimeMinutes(BigDecimal.ZERO)
                        .averageDeliveryTimeMinutes(BigDecimal.ZERO).build());
        when(operationalAnalyticsService.getPeakHours(any(), any(), anyLong()))
                .thenReturn(PeakHoursDTO.builder().peakHours(List.of()).build());
        when(customerAnalyticsService.getCustomerRetention(any(), any(), anyLong()))
                .thenReturn(CustomerRetentionDTO.builder()
                        .customersAtEnd(0).newCustomers(0).retentionRate(0.0).build());
        when(customerAnalyticsService.getCustomerLTV(anyLong()))
                .thenReturn(CustomerLTVDTO.builder().averageCustomerLTV(BigDecimal.ZERO).build());
        when(customerAnalyticsService.getCustomerSatisfaction(any(), any(), anyLong()))
                .thenReturn(CustomerSatisfactionDTO.builder().overallSatisfactionScore(BigDecimal.ZERO).build());
        when(inventoryAnalyticsService.getInventoryTurnover(any(), any(), anyLong()))
                .thenReturn(InventoryTurnoverDTO.builder()
                        .overallTurnoverRatio(BigDecimal.ZERO)
                        .ingredientTurnovers(List.of()).build());

        var result = summaryService.getAnalyticsSummary(LocalDate.now().minusDays(7), LocalDate.now(), 1L,
                BigDecimal.ZERO, BigDecimal.ZERO);
        assertNotNull(result);
        verify(financialAnalyticsService).getDailyRevenue(any(), any(), anyLong());
    }
}
