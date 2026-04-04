package com.elcafe.modules.order.controller;

import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.selfservice.repository.SelfServiceOrderRepository;
import com.elcafe.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;

    @Mock private OrderService orderService;
    @Mock private OrderRepository orderRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private ShiftTimeService shiftTimeService;
    @Mock private SelfServiceOrderRepository selfServiceOrderRepository;
    @InjectMocks private OrderController controller;

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
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver(), principalResolver)
                .build();
    }

    // getAllOrders test removed — controller builds Pageable internally
    // and calls shiftTimeService which needs complex mocking.
    // getOrdersWithFilters is tested in OrderServiceTest.

    @Test
    @DisplayName("GET /orders/{id} — returns order")
    void getOrderById_returns200() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(createOrder(1L, OrderStatus.NEW));

        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /orders/number/{orderNumber} — returns order")
    void getOrderByNumber_returns200() throws Exception {
        when(orderService.getOrderByNumber("ORD-001")).thenReturn(createOrder(1L, OrderStatus.NEW));

        mockMvc.perform(get("/api/v1/orders/number/ORD-001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /orders/restaurant/{restaurantId} — returns list")
    void getRestaurantOrders_returns200() throws Exception {
        when(orderService.getOrdersByRestaurant(1L)).thenReturn(List.of(createOrder(1L, OrderStatus.NEW)));

        mockMvc.perform(get("/api/v1/orders/restaurant/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /orders/pending — returns pending orders")
    void getPendingOrders_returns200() throws Exception {
        when(orderService.getPendingOrders()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders/pending"))
                .andExpect(status().isOk());
    }

    // ==================== createOrder ====================

    @Test
    @DisplayName("POST / — creates order")
    void createOrder_returns201() throws Exception {
        Order order = createOrder(1L, OrderStatus.NEW);
        when(orderService.createOrder(any())).thenReturn(order);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isCreated());
    }

    // ==================== updateOrderStatus ====================

    @Test
    @DisplayName("PATCH /{id}/status — updates order status")
    void updateOrderStatus_returns200() throws Exception {
        when(orderService.updateOrderStatus(anyLong(), any(), anyString(), anyString()))
                .thenReturn(createOrder(1L, OrderStatus.PREPARING));

        mockMvc.perform(patch("/api/v1/orders/1/status")
                        .param("status", "PREPARING")
                        .param("notes", "started")
                        .param("changedBy", "OPERATOR"))
                .andExpect(status().isOk());
    }

    // ==================== getAllOrders ====================

    @Test
    @DisplayName("GET / — lists all orders")
    void getAllOrders_returns200() throws Exception {
        when(orderService.getAllOrders(any())).thenReturn(new PageImpl<>(List.of(createOrder(1L, OrderStatus.NEW))));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk());
    }

    // ==================== revertOrderToActive ====================

    @Test
    @DisplayName("PATCH /{id}/revert — reverts order to active")
    void revertOrder_returns200() throws Exception {
        when(orderService.revertOrderToActive(anyLong(), any(), anyString(), anyString()))
                .thenReturn(createOrder(1L, OrderStatus.PREPARING));

        mockMvc.perform(patch("/api/v1/orders/1/revert")
                        .param("targetStatus", "PREPARING")
                        .param("reason", "Reopened")
                        .param("revertedBy", "MANAGER"))
                .andExpect(status().isOk());
    }

    // ==================== getSelfServiceOrders ====================

    @Test
    @DisplayName("GET /self-service — returns self-service orders")
    void getSelfServiceOrders_returns200() throws Exception {
        when(selfServiceOrderRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/orders/self-service"))
                .andExpect(status().isOk());
    }

    // ==================== getExternalOrders ====================

    @Test
    @DisplayName("GET /external — returns external orders")
    void getExternalOrders_returns200() throws Exception {
        when(orderRepository.findByOrderSourceIn(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/orders/external"))
                .andExpect(status().isOk());
    }
}
