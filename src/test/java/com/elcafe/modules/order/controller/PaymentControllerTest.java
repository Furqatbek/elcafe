package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.CreatePaymentRequest;
import com.elcafe.modules.order.dto.PaymentResponse;
import com.elcafe.modules.order.dto.UpdatePaymentRequest;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentControllerTest {

    private MockMvc mockMvc;
    @Mock private PaymentService paymentService;
    @InjectMocks private PaymentController controller;

    @BeforeEach
    void setUp() {
        ObjectMapper jacksonMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(jacksonMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    @Test @DisplayName("GET /orders/{id}/payments") void getPayment() throws Exception {
        when(paymentService.getPaymentByOrderId(1L)).thenReturn(PaymentResponse.builder()
                .id(1L).orderId(1L).method(PaymentMethod.CASH).status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(100000)).build());
        mockMvc.perform(get("/api/v1/orders/1/payments")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /orders/{id}/payments/{paymentId}") void getById() throws Exception {
        when(paymentService.getPaymentById(1L, 10L)).thenReturn(PaymentResponse.builder()
                .id(10L).orderId(1L).method(PaymentMethod.CASH).status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(100000)).build());
        mockMvc.perform(get("/api/v1/orders/1/payments/10")).andExpect(status().isOk());
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PaymentResponse samplePayment() {
        return PaymentResponse.builder()
                .id(1L).orderId(1L).method(PaymentMethod.CASH).status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(100000)).build();
    }

    // ==================== createPayment ====================

    @Test
    @DisplayName("POST /orders/{id}/payments — creates payment")
    void createPayment_returns201() throws Exception {
        when(paymentService.createPayment(eq(1L), any())).thenReturn(samplePayment());

        CreatePaymentRequest req = CreatePaymentRequest.builder()
                .method(PaymentMethod.CASH)
                .amount(BigDecimal.valueOf(100000))
                .build();

        mockMvc.perform(post("/api/v1/orders/1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ==================== updatePayment ====================

    @Test
    @DisplayName("PUT /orders/{id}/payments/{paymentId} — updates payment")
    void updatePayment_returns200() throws Exception {
        when(paymentService.updatePayment(eq(1L), eq(10L), any())).thenReturn(samplePayment());

        UpdatePaymentRequest req = UpdatePaymentRequest.builder()
                .method(PaymentMethod.CASH)
                .amount(BigDecimal.valueOf(120000))
                .build();

        mockMvc.perform(put("/api/v1/orders/1/payments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ==================== deletePayment ====================

    @Test
    @DisplayName("DELETE /orders/{id}/payments/{paymentId} — soft deletes payment")
    void deletePayment_returns200() throws Exception {
        doNothing().when(paymentService).deletePayment(eq(1L), eq(10L), anyString());

        mockMvc.perform(delete("/api/v1/orders/1/payments/10"))
                .andExpect(status().isOk());
    }

    // ==================== report ====================

    @Test
    @DisplayName("GET /orders/{id}/payments/report — returns payment report")
    void getPaymentReport_returns200() throws Exception {
        when(paymentService.getPaymentsByStatusAndDateRange(any(), any(), any()))
                .thenReturn(List.of(samplePayment()));

        mockMvc.perform(get("/api/v1/orders/1/payments/report")
                        .param("status", "COMPLETED")
                        .param("startDate", "2026-01-01T00:00:00+05:00")
                        .param("endDate", "2026-12-31T23:59:59+05:00"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /by-transaction — returns payment")
    void getPaymentByTransaction_returns200() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-001")).thenReturn(samplePayment());
        mockMvc.perform(get("/api/v1/orders/1/payments/by-transaction").param("transactionId", "TXN-001"))
                .andExpect(status().isOk());
    }

    // ==================== getAllPayments ====================

    @Test
    @DisplayName("GET /orders/{id}/payments/all — returns paginated payments")
    void getAllPayments_returns200() throws Exception {
        Page<PaymentResponse> page = new PageImpl<>(List.of(samplePayment()), PageRequest.of(0, 20), 1);
        when(paymentService.getAllPayments(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders/1/payments/all"))
                .andExpect(status().isOk());
    }

    // ==================== getPaymentsByStatus ====================

    @Test
    @DisplayName("GET /orders/{id}/payments/by-status — returns payments by status")
    void getPaymentsByStatus_returns200() throws Exception {
        Page<PaymentResponse> page = new PageImpl<>(List.of(samplePayment()), PageRequest.of(0, 20), 1);
        when(paymentService.getPaymentsByStatus(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders/1/payments/by-status")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk());
    }

    // ==================== getPaymentsByMethod ====================

    @Test
    @DisplayName("GET /orders/{id}/payments/by-method — returns payments by method")
    void getPaymentsByMethod_returns200() throws Exception {
        Page<PaymentResponse> page = new PageImpl<>(List.of(samplePayment()), PageRequest.of(0, 20), 1);
        when(paymentService.getPaymentsByMethod(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders/1/payments/by-method")
                        .param("method", "CASH"))
                .andExpect(status().isOk());
    }
}
