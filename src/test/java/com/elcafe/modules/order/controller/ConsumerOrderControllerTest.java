package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.consumer.OrderResponse;
import com.elcafe.modules.order.service.ConsumerOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConsumerOrderControllerTest {

    private MockMvc mockMvc;
    @Mock private ConsumerOrderService consumerOrderService;
    @InjectMocks private ConsumerOrderController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /{orderNumber} returns order")
    void getOrder_returns200() throws Exception {
        when(consumerOrderService.getOrderByNumber("ORD-001")).thenReturn(OrderResponse.builder().build());
        mockMvc.perform(get("/api/v1/consumer/orders/ORD-001")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{orderNumber}/cancel cancels order")
    void cancelOrder_returns200() throws Exception {
        when(consumerOrderService.cancelOrder(anyString(), anyString()))
                .thenReturn(OrderResponse.builder().build());
        mockMvc.perform(post("/api/v1/consumer/orders/ORD-001/cancel")
                        .param("reason", "Changed mind"))
                .andExpect(status().isOk());
    }
}
