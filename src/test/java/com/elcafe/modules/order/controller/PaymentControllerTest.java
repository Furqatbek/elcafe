package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.CreatePaymentRequest;
import com.elcafe.modules.order.dto.PaymentResponse;
import com.elcafe.modules.order.dto.UpdatePaymentRequest;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private PaymentService paymentService;
    @InjectMocks private PaymentController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private PaymentResponse buildPaymentResponse() {
        return PaymentResponse.builder()
                .id(1L).orderId(1L)
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(100000))
                .build();
    }

    // ==================== getPaymentByOrderId ====================

    @Test
    @DisplayName("GET /orders/{orderId}/payments — returns payment")
    void getPaymentByOrderId_returns200() throws Exception {
        when(paymentService.getPaymentByOrderId(1L)).thenReturn(buildPaymentResponse());

        mockMvc.perform(get("/api/v1/orders/1/payments"))
                .andExpect(status().isOk());
    }

    // ==================== getPaymentById ====================

    @Test
    @DisplayName("GET /orders/{orderId}/payments/{paymentId} — returns payment")
    void getPaymentById_returns200() throws Exception {
        when(paymentService.getPaymentById(1L, 10L)).thenReturn(buildPaymentResponse());

        mockMvc.perform(get("/api/v1/orders/1/payments/10"))
                .andExpect(status().isOk());
    }

    // ==================== createPayment ====================

    @Test
    @DisplayName("POST /orders/{orderId}/payments — creates payment")
    void createPayment_returns201() throws Exception {
        when(paymentService.createPayment(eq(1L), any())).thenReturn(buildPaymentResponse());

        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setMethod(PaymentMethod.CASH);
        req.setAmount(BigDecimal.valueOf(100000));

        mockMvc.perform(post("/api/v1/orders/1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ==================== updatePayment ====================

    @Test
    @DisplayName("PUT /orders/{orderId}/payments/{paymentId} — updates payment")
    void updatePayment_returns200() throws Exception {
        when(paymentService.updatePayment(eq(1L), eq(10L), any())).thenReturn(buildPaymentResponse());

        UpdatePaymentRequest req = new UpdatePaymentRequest();

        mockMvc.perform(put("/api/v1/orders/1/payments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ==================== deletePayment ====================

    @Test
    @DisplayName("DELETE /orders/{orderId}/payments/{paymentId} — deletes payment")
    void deletePayment_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/1/payments/10"))
                .andExpect(status().isOk());

        verify(paymentService).deletePayment(eq(1L), eq(10L), any());
    }

    // ==================== getAllPayments ====================

    @Test
    @DisplayName("GET /orders/{orderId}/payments/all — returns all payments")
    void getAllPayments_returns200() throws Exception {
        Page<PaymentResponse> page = new PageImpl<>(List.of(buildPaymentResponse()));
        when(paymentService.getAllPayments(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders/1/payments/all"))
                .andExpect(status().isOk());
    }

    // ==================== getPaymentsByStatus ====================

    @Test
    @DisplayName("GET /orders/{orderId}/payments/by-status — filters by status")
    void getPaymentsByStatus_returns200() throws Exception {
        Page<PaymentResponse> page = new PageImpl<>(List.of(buildPaymentResponse()));
        when(paymentService.getPaymentsByStatus(eq(PaymentStatus.COMPLETED), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders/1/payments/by-status")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk());
    }

    // ==================== getPaymentsByMethod ====================

    @Test
    @DisplayName("GET /orders/{orderId}/payments/by-method — filters by method")
    void getPaymentsByMethod_returns200() throws Exception {
        Page<PaymentResponse> page = new PageImpl<>(List.of(buildPaymentResponse()));
        when(paymentService.getPaymentsByMethod(eq(PaymentMethod.CASH), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders/1/payments/by-method")
                        .param("method", "CASH"))
                .andExpect(status().isOk());
    }

    // ==================== getPaymentByTransactionId ====================

    @Test
    @DisplayName("GET /orders/{orderId}/payments/by-transaction — finds by transaction ID")
    void getPaymentByTransaction_returns200() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-001")).thenReturn(buildPaymentResponse());

        mockMvc.perform(get("/api/v1/orders/1/payments/by-transaction")
                        .param("transactionId", "TXN-001"))
                .andExpect(status().isOk());
    }
}
