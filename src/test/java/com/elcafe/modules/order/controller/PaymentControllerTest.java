package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.PaymentResponse;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.service.PaymentService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;
    @Mock private PaymentService paymentService;
    @InjectMocks private PaymentController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
}
