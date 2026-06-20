package com.elcafe.modules.waiter.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.waiter.entity.WaiterKPIConfig;
import com.elcafe.modules.waiter.entity.WaiterPerformance;
import com.elcafe.modules.waiter.service.WaiterPerformanceService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WaiterPerformanceControllerTest {

    private MockMvc mockMvc;
    @Mock private WaiterPerformanceService performanceService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private WaiterPerformanceController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /restaurant/{id}/kpi-configs")
    void getKPIConfigs_returns200() throws Exception {
        when(performanceService.getKPIConfigs(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/waiter-performance/restaurant/1/kpi-configs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /kpi-config/{id}")
    void deleteKPIConfig_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/waiter-performance/kpi-config/1"))
                .andExpect(status().isOk());
        verify(performanceService).deleteKPIConfig(1L);
    }

    @Test
    @DisplayName("GET /waiter/{id}/today")
    void getTodayPerformance_returns200() throws Exception {
        when(performanceService.getTodayPerformance(1L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/waiter-performance/waiter/1/today")
                        .param("restaurantId", "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /waiter/{id}/record-complaint")
    void recordComplaint_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/waiter-performance/waiter/1/record-complaint")
                        .param("restaurantId", "1"))
                .andExpect(status().isOk());
        verify(performanceService).recordComplaint(1L, 1L);
    }

    @Test
    @DisplayName("POST /waiter/{id}/shift-start")
    void shiftStart_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/waiter-performance/waiter/1/shift-start")
                        .param("restaurantId", "1"))
                .andExpect(status().isOk());
        verify(performanceService).recordShiftStart(1L, 1L);
    }

    @Test
    @DisplayName("POST /waiter/{id}/shift-end")
    void shiftEnd_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/waiter-performance/waiter/1/shift-end")
                        .param("restaurantId", "1"))
                .andExpect(status().isOk());
        verify(performanceService).recordShiftEnd(1L, 1L);
    }
}
