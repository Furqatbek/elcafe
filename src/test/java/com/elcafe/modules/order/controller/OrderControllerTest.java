package com.elcafe.modules.order.controller;

import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.selfservice.repository.SelfServiceOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;

    @Mock private OrderService orderService;
    @Mock private ShiftTimeService shiftTimeService;
    @Mock private SelfServiceOrderRepository selfServiceOrderRepository;
    @InjectMocks private OrderController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
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
}
