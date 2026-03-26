package com.elcafe.modules.waiter.controller;

import com.elcafe.exception.GlobalExceptionHandler;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.promotion.dto.ApplyDiscountRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponResponse;
import com.elcafe.modules.promotion.enums.DiscountType;
import com.elcafe.modules.promotion.enums.PromotionType;
import com.elcafe.modules.waiter.dto.AddOrderItemRequest;
import com.elcafe.modules.waiter.dto.CreateOrderRequest;
import com.elcafe.modules.waiter.dto.OrderEventResponse;
import com.elcafe.modules.waiter.dto.UpdateOrderItemRequest;
import com.elcafe.modules.waiter.dto.WaiterMetricsResponse;
import com.elcafe.modules.waiter.enums.OrderEventType;
import com.elcafe.modules.waiter.helper.TestDataFactory;
import com.elcafe.modules.waiter.service.OrderEventService;
import com.elcafe.modules.waiter.service.WaiterOrderService;
import com.elcafe.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WaiterOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class WaiterOrderControllerTest {

    private static final String BASE_URL = "/api/v1/waiter/orders";
    private static final Long WAITER_ID = 1L;
    private static final Long ORDER_ID = 1L;
    private static final Long TABLE_ID = 1L;
    private static final Long ITEM_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WaiterOrderService waiterOrderService;

    @MockBean
    private OrderEventService orderEventService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsService userDetailsService;

    // ----- Helper methods -----

    private Order buildOrder(Long id, OrderStatus status) {
        Order order = TestDataFactory.createOrder(id, status);
        order.setOrderNumber("W123456");
        order.setSubtotal(BigDecimal.valueOf(25.00));
        order.setTotal(BigDecimal.valueOf(25.00));
        return order;
    }

    private Order buildOrderWithItems(Long id, OrderStatus status) {
        Order order = buildOrder(id, status);
        order.getItems().add(TestDataFactory.createOrderItem(ITEM_ID, 1L, "Espresso", 2, BigDecimal.valueOf(5.00)));
        order.setSubtotal(BigDecimal.valueOf(10.00));
        order.setTotal(BigDecimal.valueOf(10.00));
        return order;
    }

    // ----- 1. createOrder_returnsCreatedOrder -----

    @Test
    @DisplayName("POST /orders - creates order and returns 201")
    void createOrder_returnsCreatedOrder() throws Exception {
        CreateOrderRequest request = TestDataFactory.createOrderRequest(TABLE_ID);
        Order order = buildOrder(ORDER_ID, OrderStatus.NEW);

        when(waiterOrderService.createOrder(any(CreateOrderRequest.class), eq(WAITER_ID)))
                .thenReturn(order);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Waiter-Id", WAITER_ID)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order created successfully"))
                .andExpect(jsonPath("$.data.id").value(ORDER_ID))
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.orderNumber").value("W123456"));
    }

    // ----- 2. createOrder_withItems_returnsCreatedOrder -----

    @Test
    @DisplayName("POST /orders - creates order with items and returns 201")
    void createOrder_withItems_returnsCreatedOrder() throws Exception {
        List<AddOrderItemRequest> items = List.of(
                TestDataFactory.createAddItemRequest(1L, 2)
        );
        CreateOrderRequest request = TestDataFactory.createOrderRequest(TABLE_ID, null, items);

        Order order = buildOrderWithItems(ORDER_ID, OrderStatus.PREPARING);

        when(waiterOrderService.createOrder(any(CreateOrderRequest.class), eq(WAITER_ID)))
                .thenReturn(order);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Waiter-Id", WAITER_ID)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PREPARING"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].productName").value("Espresso"));
    }

    // ----- 3. createOrder_invalidRequest_returns400 -----

    @Test
    @DisplayName("POST /orders - missing tableId returns 400")
    void createOrder_invalidRequest_returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        // tableId is null, which violates @NotNull

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Waiter-Id", WAITER_ID)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.tableId").value("Table ID is required"));
    }

    // ----- 4. getOrder_returnsOrder -----

    @Test
    @DisplayName("GET /orders/{id} - returns order")
    void getOrder_returnsOrder() throws Exception {
        Order order = buildOrder(ORDER_ID, OrderStatus.PREPARING);

        when(waiterOrderService.getOrder(ORDER_ID)).thenReturn(order);

        mockMvc.perform(get(BASE_URL + "/{id}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order retrieved successfully"))
                .andExpect(jsonPath("$.data.id").value(ORDER_ID))
                .andExpect(jsonPath("$.data.status").value("PREPARING"));
    }

    // ----- 5. getOrder_notFound_returns404 -----

    @Test
    @DisplayName("GET /orders/{id} - not found returns 404")
    void getOrder_notFound_returns404() throws Exception {
        when(waiterOrderService.getOrder(999L))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 999"));

        mockMvc.perform(get(BASE_URL + "/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Order not found with id: 999"));
    }

    // ----- 6. addItems_returnsUpdatedOrder -----

    @Test
    @DisplayName("POST /orders/{orderId}/items - adds items and returns updated order")
    void addItems_returnsUpdatedOrder() throws Exception {
        List<AddOrderItemRequest> items = List.of(
                TestDataFactory.createAddItemRequest(1L, 3)
        );
        Order updatedOrder = buildOrderWithItems(ORDER_ID, OrderStatus.PREPARING);

        when(waiterOrderService.addItems(eq(ORDER_ID), any(), eq(WAITER_ID)))
                .thenReturn(updatedOrder);

        mockMvc.perform(post(BASE_URL + "/{orderId}/items", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Waiter-Id", WAITER_ID)
                        .content(objectMapper.writeValueAsString(items)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Items added to order successfully"))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    // ----- 7. updateItem_returnsUpdatedOrder -----

    @Test
    @DisplayName("PUT /orders/{orderId}/items/{itemId} - updates item and returns order")
    void updateItem_returnsUpdatedOrder() throws Exception {
        UpdateOrderItemRequest request = TestDataFactory.createUpdateItemRequest(5);
        Order updatedOrder = buildOrderWithItems(ORDER_ID, OrderStatus.PREPARING);

        when(waiterOrderService.updateItem(eq(ORDER_ID), eq(ITEM_ID), any(UpdateOrderItemRequest.class), eq(WAITER_ID)))
                .thenReturn(updatedOrder);

        mockMvc.perform(put(BASE_URL + "/{orderId}/items/{itemId}", ORDER_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Waiter-Id", WAITER_ID)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order item updated successfully"))
                .andExpect(jsonPath("$.data.id").value(ORDER_ID));
    }

    // ----- 8. removeItem_returnsUpdatedOrder -----

    @Test
    @DisplayName("DELETE /orders/{orderId}/items/{itemId} - removes item and returns order")
    void removeItem_returnsUpdatedOrder() throws Exception {
        Order updatedOrder = buildOrder(ORDER_ID, OrderStatus.NEW);

        when(waiterOrderService.removeItem(eq(ORDER_ID), eq(ITEM_ID), eq(WAITER_ID)))
                .thenReturn(updatedOrder);

        mockMvc.perform(delete(BASE_URL + "/{orderId}/items/{itemId}", ORDER_ID, ITEM_ID)
                        .header("X-Waiter-Id", WAITER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order item removed successfully"))
                .andExpect(jsonPath("$.data.id").value(ORDER_ID));
    }

    // ----- 9. submitToKitchen_returnsUpdatedOrder -----

    @Test
    @DisplayName("POST /orders/{orderId}/submit - submits to kitchen and returns order")
    void submitToKitchen_returnsUpdatedOrder() throws Exception {
        Order updatedOrder = buildOrder(ORDER_ID, OrderStatus.PREPARING);

        when(waiterOrderService.submitToKitchen(eq(ORDER_ID), eq(WAITER_ID)))
                .thenReturn(updatedOrder);

        mockMvc.perform(post(BASE_URL + "/{orderId}/submit", ORDER_ID)
                        .header("X-Waiter-Id", WAITER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order submitted to kitchen successfully"))
                .andExpect(jsonPath("$.data.status").value("PREPARING"));
    }

    // ----- 10. closeOrder_returnsUpdatedOrder -----

    @Test
    @DisplayName("POST /orders/{orderId}/close - closes order and returns it")
    void closeOrder_returnsUpdatedOrder() throws Exception {
        Order closedOrder = buildOrder(ORDER_ID, OrderStatus.COMPLETED);

        when(waiterOrderService.closeOrder(eq(ORDER_ID), eq(WAITER_ID)))
                .thenReturn(closedOrder);

        mockMvc.perform(post(BASE_URL + "/{orderId}/close", ORDER_ID)
                        .header("X-Waiter-Id", WAITER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order closed successfully"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    // ----- 11. requestBill_returnsOrder -----

    @Test
    @DisplayName("POST /orders/{orderId}/bill - requests bill and returns order")
    void requestBill_returnsOrder() throws Exception {
        Order order = buildOrder(ORDER_ID, OrderStatus.PREPARING);

        when(waiterOrderService.requestBill(eq(ORDER_ID), eq(WAITER_ID)))
                .thenReturn(order);

        mockMvc.perform(post(BASE_URL + "/{orderId}/bill", ORDER_ID)
                        .header("X-Waiter-Id", WAITER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Bill requested successfully"))
                .andExpect(jsonPath("$.data.id").value(ORDER_ID));
    }

    // ----- 12. getTableOrders_returnsList -----

    @Test
    @DisplayName("GET /orders/table/{tableId} - returns list of table orders")
    void getTableOrders_returnsList() throws Exception {
        Order order1 = buildOrder(1L, OrderStatus.NEW);
        Order order2 = buildOrder(2L, OrderStatus.PREPARING);

        when(waiterOrderService.getTableOrders(TABLE_ID))
                .thenReturn(List.of(order1, order2));

        mockMvc.perform(get(BASE_URL + "/table/{tableId}", TABLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Table orders retrieved successfully"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2));
    }

    // ----- 13. getWaiterOrders_returnsList -----

    @Test
    @DisplayName("GET /orders/waiter/{waiterId}/history - returns waiter order history")
    void getWaiterOrders_returnsList() throws Exception {
        Order order1 = buildOrder(1L, OrderStatus.COMPLETED);
        Order order2 = buildOrder(2L, OrderStatus.COMPLETED);

        when(waiterOrderService.getWaiterOrderHistory(WAITER_ID))
                .thenReturn(List.of(order1, order2));

        mockMvc.perform(get(BASE_URL + "/waiter/{waiterId}/history", WAITER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Waiter order history retrieved successfully"))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // ----- 14. getOngoingOrders_returnsList -----

    @Test
    @DisplayName("GET /orders/waiter/{waiterId}/ongoing - returns waiter ongoing orders")
    void getOngoingOrders_returnsList() throws Exception {
        Order order1 = buildOrder(1L, OrderStatus.NEW);
        Order order2 = buildOrder(2L, OrderStatus.PREPARING);

        when(waiterOrderService.getWaiterOngoingOrders(WAITER_ID))
                .thenReturn(List.of(order1, order2));

        mockMvc.perform(get(BASE_URL + "/waiter/{waiterId}/ongoing", WAITER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Waiter ongoing orders retrieved successfully"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].status").value("NEW"))
                .andExpect(jsonPath("$.data[1].status").value("PREPARING"));
    }

    // ----- 15. markItemDelivered_returnsOrder -----

    @Test
    @DisplayName("POST /orders/{orderId}/items/{itemId}/deliver - marks item delivered")
    void markItemDelivered_returnsOrder() throws Exception {
        Order order = buildOrderWithItems(ORDER_ID, OrderStatus.PREPARING);

        when(waiterOrderService.markItemDelivered(eq(ORDER_ID), eq(ITEM_ID), eq(WAITER_ID)))
                .thenReturn(order);

        mockMvc.perform(post(BASE_URL + "/{orderId}/items/{itemId}/deliver", ORDER_ID, ITEM_ID)
                        .header("X-Waiter-Id", WAITER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Item marked as delivered successfully"))
                .andExpect(jsonPath("$.data.id").value(ORDER_ID));
    }

    // ----- 16. applyDiscount_returnsUpdatedOrder -----

    @Test
    @DisplayName("POST /orders/{orderId}/discount - applies discount and returns order")
    void applyDiscount_returnsUpdatedOrder() throws Exception {
        ApplyDiscountRequest request = ApplyDiscountRequest.builder()
                .discountType(DiscountType.MANUAL)
                .manualDiscountAmount(BigDecimal.valueOf(5.00))
                .discountReason("Loyal customer")
                .build();

        Order discountedOrder = buildOrder(ORDER_ID, OrderStatus.PREPARING);
        discountedOrder.setDiscount(BigDecimal.valueOf(5.00));
        discountedOrder.setTotal(BigDecimal.valueOf(20.00));

        when(waiterOrderService.applyDiscount(eq(ORDER_ID), any(ApplyDiscountRequest.class), eq(WAITER_ID)))
                .thenReturn(discountedOrder);

        mockMvc.perform(post(BASE_URL + "/{orderId}/discount", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Waiter-Id", WAITER_ID)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Discount applied successfully"))
                .andExpect(jsonPath("$.data.discount").value(5.00))
                .andExpect(jsonPath("$.data.total").value(20.00));
    }

    // ----- 17. removeDiscount_returnsUpdatedOrder -----

    @Test
    @DisplayName("DELETE /orders/{orderId}/discount - removes discount and returns order")
    void removeDiscount_returnsUpdatedOrder() throws Exception {
        Order order = buildOrder(ORDER_ID, OrderStatus.PREPARING);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.valueOf(25.00));

        when(waiterOrderService.removeDiscount(eq(ORDER_ID), eq(WAITER_ID)))
                .thenReturn(order);

        mockMvc.perform(delete(BASE_URL + "/{orderId}/discount", ORDER_ID)
                        .header("X-Waiter-Id", WAITER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Discount removed successfully"))
                .andExpect(jsonPath("$.data.discount").value(0));
    }

    // ----- 18. getOrderHistory_returnsEventList -----

    @Test
    @DisplayName("GET /orders/{orderId}/history - returns order event history")
    void getOrderHistory_returnsEventList() throws Exception {
        OrderEventResponse event1 = OrderEventResponse.builder()
                .id(1L)
                .orderId(ORDER_ID)
                .eventType(OrderEventType.ORDER_CREATED)
                .triggeredBy("Test Waiter")
                .createdAt(LocalDateTime.now())
                .build();
        OrderEventResponse event2 = OrderEventResponse.builder()
                .id(2L)
                .orderId(ORDER_ID)
                .eventType(OrderEventType.ORDER_SUBMITTED_TO_KITCHEN)
                .triggeredBy("Test Waiter")
                .createdAt(LocalDateTime.now())
                .build();

        when(orderEventService.getOrderHistory(ORDER_ID))
                .thenReturn(List.of(event1, event2));

        mockMvc.perform(get(BASE_URL + "/{orderId}/history", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order history retrieved successfully"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].eventType").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.data[1].eventType").value("ORDER_SUBMITTED_TO_KITCHEN"));
    }

    // ----- 19. getWaiterMetrics_returnsMetrics -----

    @Test
    @DisplayName("GET /orders/waiter/{waiterId}/metrics - returns waiter metrics")
    void getWaiterMetrics_returnsMetrics() throws Exception {
        WaiterMetricsResponse metrics = WaiterMetricsResponse.builder()
                .totalRevenue(BigDecimal.valueOf(1500.00))
                .totalOrders(30L)
                .averageTicket(BigDecimal.valueOf(50.00))
                .weeklyActivity(List.of())
                .recentTransactions(List.of())
                .build();

        when(waiterOrderService.getWaiterMetrics(eq(WAITER_ID), eq("weekly")))
                .thenReturn(metrics);

        mockMvc.perform(get(BASE_URL + "/waiter/{waiterId}/metrics", WAITER_ID)
                        .param("period", "weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Waiter metrics retrieved successfully"))
                .andExpect(jsonPath("$.data.totalRevenue").value(1500.00))
                .andExpect(jsonPath("$.data.totalOrders").value(30))
                .andExpect(jsonPath("$.data.averageTicket").value(50.00));
    }

    // ----- 20. getWaiterMetrics_defaultPeriod -----

    @Test
    @DisplayName("GET /orders/waiter/{waiterId}/metrics - uses default weekly period")
    void getWaiterMetrics_defaultPeriod() throws Exception {
        WaiterMetricsResponse metrics = WaiterMetricsResponse.builder()
                .totalRevenue(BigDecimal.valueOf(500.00))
                .totalOrders(10L)
                .averageTicket(BigDecimal.valueOf(50.00))
                .weeklyActivity(List.of())
                .recentTransactions(List.of())
                .build();

        when(waiterOrderService.getWaiterMetrics(eq(WAITER_ID), eq("weekly")))
                .thenReturn(metrics);

        mockMvc.perform(get(BASE_URL + "/waiter/{waiterId}/metrics", WAITER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRevenue").value(500.00));
    }

    // ----- 21. validateCoupon_returnsResponse -----

    @Test
    @DisplayName("POST /orders/{orderId}/validate-coupon - validates coupon and returns response")
    void validateCoupon_returnsResponse() throws Exception {
        ValidateCouponResponse response = ValidateCouponResponse.valid(
                "SAVE10", 5L, "10% Off", PromotionType.PERCENTAGE,
                BigDecimal.valueOf(10), BigDecimal.valueOf(2.50));

        when(waiterOrderService.validateCoupon(eq(ORDER_ID), eq("SAVE10")))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/{orderId}/validate-coupon", ORDER_ID)
                        .param("couponCode", "SAVE10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Coupon validation completed"))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.code").value("SAVE10"))
                .andExpect(jsonPath("$.data.calculatedDiscount").value(2.50));
    }

    // ----- 22. validateCoupon_invalidCoupon -----

    @Test
    @DisplayName("POST /orders/{orderId}/validate-coupon - invalid coupon returns valid=false")
    void validateCoupon_invalidCoupon_returnsInvalid() throws Exception {
        ValidateCouponResponse response = ValidateCouponResponse.invalid("Coupon expired");

        when(waiterOrderService.validateCoupon(eq(ORDER_ID), eq("EXPIRED")))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/{orderId}/validate-coupon", ORDER_ID)
                        .param("couponCode", "EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errorMessage").value("Coupon expired"));
    }

    // ----- 23. applyDiscount_couponType -----

    @Test
    @DisplayName("POST /orders/{orderId}/discount - applies coupon discount")
    void applyDiscount_couponType_returnsUpdatedOrder() throws Exception {
        ApplyDiscountRequest request = ApplyDiscountRequest.builder()
                .discountType(DiscountType.COUPON)
                .couponCode("SAVE10")
                .build();

        Order discountedOrder = buildOrder(ORDER_ID, OrderStatus.PREPARING);
        discountedOrder.setDiscount(BigDecimal.valueOf(2.50));
        discountedOrder.setCouponCode("SAVE10");
        discountedOrder.setTotal(BigDecimal.valueOf(22.50));

        when(waiterOrderService.applyDiscount(eq(ORDER_ID), any(ApplyDiscountRequest.class), eq(WAITER_ID)))
                .thenReturn(discountedOrder);

        mockMvc.perform(post(BASE_URL + "/{orderId}/discount", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Waiter-Id", WAITER_ID)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.discount").value(2.50))
                .andExpect(jsonPath("$.data.couponCode").value("SAVE10"))
                .andExpect(jsonPath("$.data.total").value(22.50));
    }

    // ----- 24. applyDiscount_missingType_returns400 -----

    @Test
    @DisplayName("POST /orders/{orderId}/discount - missing discountType returns 400")
    void applyDiscount_missingType_returns400() throws Exception {
        ApplyDiscountRequest request = ApplyDiscountRequest.builder()
                .manualDiscountAmount(BigDecimal.valueOf(5.00))
                .build();
        // discountType is null, violates @NotNull

        mockMvc.perform(post(BASE_URL + "/{orderId}/discount", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Waiter-Id", WAITER_ID)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.discountType").value("Discount type is required"));
    }

    // ----- 25. getTableOrders_emptyList -----

    @Test
    @DisplayName("GET /orders/table/{tableId} - returns empty list when no active orders")
    void getTableOrders_emptyList() throws Exception {
        when(waiterOrderService.getTableOrders(TABLE_ID))
                .thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/table/{tableId}", TABLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
