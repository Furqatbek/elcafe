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

import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("GET /{orderNumber}/status passes the tracking token and returns 200")
    void getStatus_withToken_returns200() throws Exception {
        when(trackingService.getOrderTracking(eq("ORD-001"), eq("tok")))
                .thenReturn(OrderTrackingResponse.builder().build());
        mockMvc.perform(get("/api/v1/public/orders/ORD-001/status").param("token", "tok"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{orderNumber}/status without a token → 400 (token is required)")
    void getStatus_missingToken_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/public/orders/ORD-001/status"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /{orderNumber}/eta passes the tracking token and returns 200")
    void getEta_withToken_returns200() throws Exception {
        when(trackingService.calculateETA(eq("ORD-001"), eq("tok")))
                .thenReturn(OrderTrackingResponse.ETAInfo.builder().build());
        mockMvc.perform(get("/api/v1/public/orders/ORD-001/eta").param("token", "tok"))
                .andExpect(status().isOk());
    }
}
