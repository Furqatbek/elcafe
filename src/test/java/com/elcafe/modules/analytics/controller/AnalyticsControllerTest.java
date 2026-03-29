package com.elcafe.modules.analytics.controller;

import com.elcafe.modules.analytics.service.AnalyticsSummaryService;
import com.elcafe.modules.analytics.service.CustomerAnalyticsService;
import com.elcafe.modules.analytics.service.FinancialAnalyticsService;
import com.elcafe.modules.analytics.service.InventoryAnalyticsService;
import com.elcafe.modules.analytics.service.OperationalAnalyticsService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    private MockMvc mockMvc;
    @Mock private FinancialAnalyticsService financialService;
    @Mock private OperationalAnalyticsService operationalService;
    @Mock private CustomerAnalyticsService customerService;
    @Mock private InventoryAnalyticsService inventoryService;
    @Mock private AnalyticsSummaryService summaryService;
    @Mock private RestaurantAuthorizationService authService;
    @InjectMocks private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test @DisplayName("GET /financial/daily-revenue") void dailyRevenue() throws Exception {
        when(financialService.getDailyRevenue(any(), any(), anyLong())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/analytics/financial/daily-revenue")
                .param("restaurantId", "1").param("startDate", "2026-03-01").param("endDate", "2026-03-31"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /financial/sales-by-category") void salesByCategory() throws Exception {
        when(financialService.getSalesPerCategory(any(), any(), anyLong())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/analytics/financial/sales-by-category")
                .param("restaurantId", "1").param("startDate", "2026-03-01").param("endDate", "2026-03-31"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /operational/sales-per-hour") void salesPerHour() throws Exception {
        when(operationalService.getSalesPerHour(any(), any(), anyLong())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/analytics/operational/sales-per-hour")
                .param("restaurantId", "1").param("startDate", "2026-03-01").param("endDate", "2026-03-31"))
                .andExpect(status().isOk());
    }
}
