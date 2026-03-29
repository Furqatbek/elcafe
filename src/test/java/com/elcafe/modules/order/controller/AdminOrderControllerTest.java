package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

    private MockMvc mockMvc;
    @Mock private OrderService orderService;
    @InjectMocks private AdminOrderController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /{orderId} returns order")
    void getOrder_returns200() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(createOrder(1L, OrderStatus.NEW));
        mockMvc.perform(get("/api/v1/admin/orders/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{orderId}/accept accepts order")
    void acceptOrder_returns200() throws Exception {
        when(orderService.updateOrderStatus(eq(1L), eq(OrderStatus.ACCEPTED), any(), any()))
                .thenReturn(createOrder(1L, OrderStatus.ACCEPTED));
        mockMvc.perform(post("/api/v1/admin/orders/1/accept")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{orderId}/cancel cancels order")
    void cancelOrder_returns200() throws Exception {
        when(orderService.updateOrderStatus(eq(1L), eq(OrderStatus.CANCELLED), any(), any()))
                .thenReturn(createOrder(1L, OrderStatus.CANCELLED));
        mockMvc.perform(post("/api/v1/admin/orders/1/cancel")).andExpect(status().isOk());
    }
}
