package com.elcafe.modules.financial.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardControllerTest {

    private MockMvc mockMvc;
    @Mock private DashboardService dashboardService;
    @Mock private ShiftTimeService shiftTimeService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private DashboardController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        DashboardResponse response = new DashboardResponse();
        lenient().when(dashboardService.getTodaySummary(anyLong())).thenReturn(response);
        lenient().when(dashboardService.getWeekSummary(anyLong())).thenReturn(response);
        lenient().when(dashboardService.getMonthSummary(anyLong())).thenReturn(response);
    }

    @Test @DisplayName("GET /today") void today() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/today").param("restaurantId", "1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /week") void week() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/week").param("restaurantId", "1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /month") void month() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/month").param("restaurantId", "1")).andExpect(status().isOk());
    }
}
