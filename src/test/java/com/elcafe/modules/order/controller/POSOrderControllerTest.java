package com.elcafe.modules.order.controller;

import com.elcafe.common.audit.service.AuditService;
import com.elcafe.common.security.service.FinancialOperationSecurityService;
import com.elcafe.modules.order.dto.pos.CreatePOSOrderRequest;
import com.elcafe.modules.order.dto.pos.ModifyOrderItemRequest;
import com.elcafe.modules.order.dto.pos.POSOrderResponse;
import com.elcafe.modules.order.dto.pos.PaymentRequestDTO;
import com.elcafe.modules.order.dto.pos.PaymentResponseDTO;
import com.elcafe.modules.order.dto.pos.SplitBillDTO;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.IdempotencyService;
import com.elcafe.modules.order.service.POSOrderDiscountService;
import com.elcafe.modules.order.service.POSOrderFeeService;
import com.elcafe.modules.order.service.POSOrderItemService;
import com.elcafe.modules.order.service.POSOrderService;
import com.elcafe.modules.order.service.POSSplitBillService;
import com.elcafe.modules.order.service.POSTableService;
import com.elcafe.modules.order.service.PaymentService;
import com.elcafe.modules.promotion.service.HappyHourService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class POSOrderControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private POSOrderService posOrderService;
    @Mock private POSOrderItemService posOrderItemService;
    @Mock private POSOrderDiscountService posOrderDiscountService;
    @Mock private POSOrderFeeService posOrderFeeService;
    @Mock private POSSplitBillService posSplitBillService;
    @Mock private POSTableService posTableService;
    @Mock private PaymentService paymentService;
    @Mock private HappyHourService happyHourService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private FinancialOperationSecurityService financialSecurityService;
    @Mock private AuditService auditService;
    @Mock private OrderRepository orderRepository;

    @InjectMocks private POSOrderController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private POSOrderResponse buildResponse() {
        return POSOrderResponse.builder()
                .id(1L).orderNumber("ORD-001").status("NEW")
                .subtotal(BigDecimal.valueOf(80000)).total(BigDecimal.valueOf(80000))
                .items(List.of()).build();
    }

    // ==================== createOrder ====================

    @Test
    @DisplayName("POST / — creates order returns 201")
    void createOrder_returns201() throws Exception {
        when(posOrderService.createOrder(any())).thenReturn(buildResponse());

        CreatePOSOrderRequest req = new CreatePOSOrderRequest();
        req.setRestaurantId(1L);
        req.setOrderType(CreatePOSOrderRequest.OrderType.DINE_IN);
        req.setItems(List.of());

        mockMvc.perform(post("/api/v1/pos/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ==================== getOrder ====================

    @Test
    @DisplayName("GET /{orderId} — returns order")
    void getOrder_returns200() throws Exception {
        when(posOrderService.getOrderById(1L)).thenReturn(buildResponse());

        mockMvc.perform(get("/api/v1/pos/orders/1"))
                .andExpect(status().isOk());
    }

    // ==================== getOpenDineInOrders ====================

    @Test
    @DisplayName("GET /open/{restaurantId} — returns open orders")
    void getOpenOrders_returns200() throws Exception {
        when(posOrderService.getOpenDineInOrders(1L)).thenReturn(List.of(buildResponse()));

        mockMvc.perform(get("/api/v1/pos/orders/open/1"))
                .andExpect(status().isOk());
    }

    // ==================== addItem ====================

    @Test
    @DisplayName("POST /{orderId}/items — adds item")
    void addItem_returns200() throws Exception {
        Order order = createOrder(1L, OrderStatus.PREPARING);
        when(posOrderItemService.addItemToOrder(eq(1L), any())).thenReturn(order);

        ModifyOrderItemRequest req = new ModifyOrderItemRequest();
        req.setProductId(1L);
        req.setQuantity(2);

        mockMvc.perform(post("/api/v1/pos/orders/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ==================== removeItem ====================

    @Test
    @DisplayName("DELETE /{orderId}/items/{itemId} — removes item")
    void removeItem_returns200() throws Exception {
        when(posOrderItemService.removeItemFromOrder(1L, 10L)).thenReturn(createOrder(1L, OrderStatus.PREPARING));

        mockMvc.perform(delete("/api/v1/pos/orders/1/items/10"))
                .andExpect(status().isOk());
    }

    // ==================== updateItemQuantity ====================

    @Test
    @DisplayName("PATCH /{orderId}/items/{itemId}/quantity — updates quantity")
    void updateQuantity_returns200() throws Exception {
        when(posOrderItemService.updateItemQuantity(1L, 10L, 3)).thenReturn(createOrder(1L, OrderStatus.PREPARING));

        mockMvc.perform(patch("/api/v1/pos/orders/1/items/10/quantity")
                        .param("quantity", "3"))
                .andExpect(status().isOk());
    }

    // ==================== splitBill ====================

    @Test
    @DisplayName("POST /{orderId}/split — splits bill")
    void splitBill_returns200() throws Exception {
        SplitBillDTO.SplitBillResponse response = SplitBillDTO.SplitBillResponse.builder()
                .orderId(1L).mode(SplitBillDTO.SplitMode.EVEN).splits(List.of()).build();
        when(posSplitBillService.splitBill(eq(1L), any())).thenReturn(response);

        SplitBillDTO req = SplitBillDTO.builder().mode(SplitBillDTO.SplitMode.EVEN).numPeople(2).build();

        mockMvc.perform(post("/api/v1/pos/orders/1/split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ==================== processPayment ====================

    @Test
    @DisplayName("POST /{orderId}/payments — processes payment")
    void processPayment_returns200() throws Exception {
        PaymentResponseDTO response = PaymentResponseDTO.builder().orderId(1L).build();
        when(paymentService.processPOSPayment(eq(1L), any())).thenReturn(response);

        PaymentRequestDTO req = new PaymentRequestDTO();
        req.setMethod(PaymentMethod.CASH);
        req.setAmount(BigDecimal.valueOf(100000));
        req.setAmountTendered(BigDecimal.valueOf(100000));

        mockMvc.perform(post("/api/v1/pos/orders/1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ==================== getPaymentSummary ====================

    @Test
    @DisplayName("GET /{orderId}/payments — returns payment summary")
    void getPaymentSummary_returns200() throws Exception {
        PaymentResponseDTO response = PaymentResponseDTO.builder().orderId(1L).build();
        when(paymentService.getPOSPaymentSummary(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/pos/orders/1/payments"))
                .andExpect(status().isOk());
    }

    // ==================== serviceFee ====================

    @Test
    @DisplayName("POST /{orderId}/service-fee — applies service fee")
    void applyServiceFee_returns200() throws Exception {
        when(posOrderFeeService.applyServiceFee(eq(1L), any())).thenReturn(createOrder(1L, OrderStatus.PREPARING));

        mockMvc.perform(post("/api/v1/pos/orders/1/service-fee")
                        .param("percent", "10"))
                .andExpect(status().isOk());
    }

    // ==================== entryFee ====================

    @Test
    @DisplayName("POST /{orderId}/entry-fee — applies entry fee")
    void applyEntryFee_returns200() throws Exception {
        when(posOrderFeeService.applyEntryFee(eq(1L), any())).thenReturn(createOrder(1L, OrderStatus.PREPARING));

        mockMvc.perform(post("/api/v1/pos/orders/1/entry-fee")
                        .param("amount", "5000"))
                .andExpect(status().isOk());
    }

    // ==================== close ====================

    @Test
    @DisplayName("POST /{orderId}/close — closes order")
    void closeOrder_returns200() throws Exception {
        when(posTableService.closeOrderAndReleaseTable(1L)).thenReturn(createOrder(1L, OrderStatus.DELIVERED));

        mockMvc.perform(post("/api/v1/pos/orders/1/close"))
                .andExpect(status().isOk());
    }

    // ==================== changeTable ====================

    @Test
    @DisplayName("PATCH /{orderId}/change-table — changes table")
    void changeTable_returns200() throws Exception {
        when(posTableService.changeTable(1L, 5L)).thenReturn(createOrder(1L, OrderStatus.PREPARING));

        mockMvc.perform(patch("/api/v1/pos/orders/1/change-table")
                        .param("newTableId", "5"))
                .andExpect(status().isOk());
    }

    // ==================== discount ====================

    @Test
    @DisplayName("DELETE /{orderId}/discount — removes discount")
    void removeDiscount_returns200() throws Exception {
        when(posOrderDiscountService.removeDiscount(1L)).thenReturn(createOrder(1L, OrderStatus.PREPARING));

        mockMvc.perform(delete("/api/v1/pos/orders/1/discount"))
                .andExpect(status().isOk());
    }

    // ==================== availability ====================

    @Test
    @DisplayName("GET /products/{id}/availability — returns availability")
    void getAvailability_returns200() throws Exception {
        when(posOrderService.getProductAvailability(eq(1L), eq(1L))).thenReturn(null);

        mockMvc.perform(get("/api/v1/pos/orders/products/1/availability")
                        .param("restaurantId", "1"))
                .andExpect(status().isOk());
    }
}
