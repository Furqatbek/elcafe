package com.elcafe.modules.financial.controller;

import com.elcafe.modules.financial.dto.DashboardResponse;
import com.elcafe.modules.financial.service.DashboardService;
import com.elcafe.modules.financial.service.ShiftTimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private MockMvc mockMvc;
    @Mock private DashboardService dashboardService;
    @Mock private ShiftTimeService shiftTimeService;
    @InjectMocks private DashboardController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private void stubDashboard() {
        when(dashboardService.getDashboard(anyLong(), any(), any())).thenReturn(new DashboardResponse());
        when(dashboardService.getTodaySummary(anyLong())).thenReturn(new DashboardResponse());
        when(dashboardService.getWeekSummary(anyLong())).thenReturn(new DashboardResponse());
        when(dashboardService.getMonthSummary(anyLong())).thenReturn(new DashboardResponse());
    }

    @Test @DisplayName("GET /today") void today() throws Exception {
        stubDashboard();
        mockMvc.perform(get("/api/v1/dashboard/today").param("restaurantId", "1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /week") void week() throws Exception {
        stubDashboard();
        mockMvc.perform(get("/api/v1/dashboard/week").param("restaurantId", "1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /month") void month() throws Exception {
        stubDashboard();
        mockMvc.perform(get("/api/v1/dashboard/month").param("restaurantId", "1")).andExpect(status().isOk());
    }
}
