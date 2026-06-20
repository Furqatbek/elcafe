package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.consumer.CreateOrderRequest;
import com.elcafe.modules.order.dto.consumer.OrderResponse;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.order.service.ConsumerOrderService;
import com.elcafe.modules.promotion.dto.ValidateCouponResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConsumerOrderControllerTest {

    private MockMvc mockMvc;
    @Mock private ConsumerOrderService consumerOrderService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
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

    @Test
    @DisplayName("POST / — place order")
    void placeOrder_returns201() throws Exception {
        when(consumerOrderService.placeOrder(any())).thenReturn(OrderResponse.builder().orderNumber("ORD-002").build());

        ObjectMapper objectMapper = new ObjectMapper();
        CreateOrderRequest request = CreateOrderRequest.builder()
                .restaurantId(1L)
                .orderSource(OrderSource.WEBSITE)
                .paymentMethod("CASH")
                .items(List.of(CreateOrderRequest.OrderItemRequest.builder()
                        .productId(1L).quantity(1).build()))
                .build();

        mockMvc.perform(post("/api/v1/consumer/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /validate-coupon — validates coupon")
    void validateCoupon_returns200() throws Exception {
        when(consumerOrderService.validateCoupon(anyLong(), anyString(), any(), any(), any()))
                .thenReturn(ValidateCouponResponse.builder().valid(true).build());

        mockMvc.perform(post("/api/v1/consumer/orders/validate-coupon")
                        .param("restaurantId", "1")
                        .param("couponCode", "SAVE10")
                        .param("orderTotal", "50000"))
                .andExpect(status().isOk());
    }
}
