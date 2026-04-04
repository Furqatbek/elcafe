package com.elcafe.modules.order.controller;

import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

    private MockMvc mockMvc;
    @Mock private OrderService orderService;
    @InjectMocks private AdminOrderController controller;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L);
        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(UserPrincipal.class);
            }
            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(principalResolver)
                .build();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test @DisplayName("GET /{orderId}") void getOrder() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(createOrder(1L, OrderStatus.NEW));
        mockMvc.perform(get("/api/v1/admin/orders/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET / — list orders")
    void getAllOrders_returns200() throws Exception {
        when(orderService.getAllOrders(any())).thenReturn(new PageImpl<>(java.util.List.of(createOrder(1L, OrderStatus.NEW))));
        mockMvc.perform(get("/api/v1/admin/orders")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /{orderId}/status — update status")
    void updateStatus_returns200() throws Exception {
        when(orderService.updateOrderStatus(anyLong(), any(), anyString(), anyString()))
                .thenReturn(createOrder(1L, OrderStatus.PREPARING));

        String body = objectMapper.writeValueAsString(
                java.util.Map.of("status", "PREPARING", "notes", "started"));

        mockMvc.perform(patch("/api/v1/admin/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{orderId}/accept — accept order")
    void acceptOrder_returns200() throws Exception {
        when(orderService.acceptOrder(anyLong(), anyString(), anyString()))
                .thenReturn(createOrder(1L, OrderStatus.ACCEPTED));

        mockMvc.perform(post("/api/v1/admin/orders/1/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{orderId}/reject — reject order")
    void rejectOrder_returns200() throws Exception {
        when(orderService.rejectOrder(anyLong(), anyString(), anyString()))
                .thenReturn(createOrder(1L, OrderStatus.REJECTED));

        String body = objectMapper.writeValueAsString(java.util.Map.of("reason", "Out of stock"));

        mockMvc.perform(post("/api/v1/admin/orders/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{orderId}/cancel — cancel order")
    void cancelOrder_returns200() throws Exception {
        when(orderService.cancelOrder(anyLong(), anyString(), anyString()))
                .thenReturn(createOrder(1L, OrderStatus.CANCELLED));

        String body = objectMapper.writeValueAsString(java.util.Map.of("reason", "Customer request"));

        mockMvc.perform(post("/api/v1/admin/orders/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
