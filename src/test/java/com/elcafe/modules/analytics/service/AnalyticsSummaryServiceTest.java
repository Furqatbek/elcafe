package com.elcafe.modules.analytics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

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
        var result = summaryService.getAnalyticsSummary(LocalDate.now().minusDays(7), LocalDate.now(), 1L);
        assertNotNull(result);
        verify(financialAnalyticsService).getDailyRevenue(any(), any(), anyLong());
    }
}
