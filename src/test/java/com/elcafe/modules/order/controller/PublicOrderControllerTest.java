package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.OrderTrackingResponse;
import com.elcafe.modules.order.service.OrderTrackingService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicOrderControllerTest {

    private MockMvc mockMvc;
    @Mock private OrderTrackingService trackingService;
    @InjectMocks private PublicOrderController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /{orderNumber}/status returns tracking")
    void getStatus_returns200() throws Exception {
        when(trackingService.getOrderTracking("ORD-001")).thenReturn(OrderTrackingResponse.builder().build());
        mockMvc.perform(get("/api/v1/public/orders/ORD-001/status")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /track returns orders by phone")
    void trackByPhone_returns200() throws Exception {
        when(trackingService.getRecentOrdersByPhone("+998901111111")).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/public/orders/track").param("phone", "+998901111111"))
                .andExpect(status().isOk());
    }
}
