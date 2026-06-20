package com.elcafe.modules.order.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import org.springframework.data.domain.PageRequest;

import static com.elcafe.modules.order.enums.OrderSource.*;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderControllerTest {

    private MockMvc mockMvc;

    @Mock private OrderService orderService;
    @Mock private OrderRepository orderRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private ShiftTimeService shiftTimeService;
    @Mock private SelfServiceOrderRepository selfServiceOrderRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
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
        ObjectMapper jacksonMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(jacksonMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver(), principalResolver)
                .setMessageConverters(converter)
                .build();
    }

    // ==================== createOrder ====================

    @Test
    @DisplayName("POST /orders — creates order")
    void createOrder_returns201() throws Exception {
        when(restaurantRepository.findAnyActiveRestaurant()).thenReturn(java.util.Optional.of(
                com.elcafe.modules.restaurant.entity.Restaurant.builder().id(1L).name("R").build()));
        when(orderService.createOrder(any())).thenReturn(createOrder(1L, OrderStatus.NEW));

        // Use minimal JSON — Order entity has computed @JsonProperty getters (fullyPaid, totalPaid,
        // remainingBalance, tableIdList, etc.) that have no setters, so serializing the full entity
        // and deserializing back fails with FAIL_ON_UNKNOWN_PROPERTIES.
        String body = """
                {
                    "orderNumber": "W123456",
                    "orderType": "DINE_IN",
                    "orderSource": "WAITER",
                    "status": "NEW",
                    "subtotal": 0,
                    "deliveryFee": 0,
                    "tax": 0,
                    "discount": 0,
                    "total": 0
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // ==================== getAllOrders ====================

    @Test
    @DisplayName("GET /orders — returns paginated orders")
    void getAllOrders_returns200() throws Exception {
        Page<Order> page = new PageImpl<>(List.of(createOrder(1L, OrderStatus.NEW)), PageRequest.of(0, 20), 1);
        when(orderService.getAllOrders(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk());
    }

    // ==================== getSelfServiceOrders ====================

    @Test
    @DisplayName("GET /orders/self-service — returns self-service orders")
    void getSelfServiceOrders_returns200() throws Exception {
        when(selfServiceOrderRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/orders/self-service"))
                .andExpect(status().isOk());
    }

    // ==================== getExternalOrders ====================

    @Test
    @DisplayName("GET /orders/external — returns external orders")
    void getExternalOrders_returns200() throws Exception {
        when(orderRepository.findByOrderSourceIn(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/orders/external"))
                .andExpect(status().isOk());
    }

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

    @Test
    @DisplayName("GET /orders/by-shift/{shiftId} — returns orders for shift")
    void getOrdersByShift_returns200() throws Exception {
        when(orderRepository.findByShiftIdWithItems(1L))
                .thenReturn(List.of(createOrder(1L, OrderStatus.COMPLETED)));

        mockMvc.perform(get("/api/v1/orders/by-shift/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("by-shift method @PreAuthorize permits ADMIN, OPERATOR and WAITER")
    void getOrdersByShift_methodAllowsWaiter() throws Exception {
        java.lang.reflect.Method m =
                com.elcafe.modules.order.controller.OrderController.class
                        .getMethod("getOrdersByShift", Long.class);
        org.springframework.security.access.prepost.PreAuthorize ann =
                m.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
        assertEquals(true, ann != null,
                "by-shift handler must override class-level @PreAuthorize to permit WAITER");
        String expr = ann.value();
        assertEquals(true, expr.contains("ADMIN"),    "ADMIN missing from " + expr);
        assertEquals(true, expr.contains("OPERATOR"), "OPERATOR missing from " + expr);
        assertEquals(true, expr.contains("WAITER"),   "WAITER missing from " + expr);
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

}
