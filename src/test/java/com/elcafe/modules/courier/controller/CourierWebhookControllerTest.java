package com.elcafe.modules.courier.controller;

import com.elcafe.modules.courier.service.LocalCourierAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourierWebhookControllerTest {
    private static final String SECRET = "test-courier-secret";
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private LocalCourierAdapter courierAdapter;
    @InjectMocks private CourierWebhookController controller;

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private String body() throws Exception {
        return objectMapper.writeValueAsString(Map.of("trackingId", "TRACK-001", "status", "DELIVERED"));
    }

    @Test @DisplayName("valid X-Webhook-Secret → processes webhook")
    void validSecret_processed() throws Exception {
        mockMvc.perform(post("/api/v1/courier/webhook/delivery-status")
                .header("X-Webhook-Secret", SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
                .andExpect(status().isOk());
        verify(courierAdapter).updateDeliveryStatus("TRACK-001", "DELIVERED");
    }

    @Test @DisplayName("missing secret → 401, order state untouched")
    void missingSecret_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/courier/webhook/delivery-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
                .andExpect(status().isUnauthorized());
        verify(courierAdapter, never()).updateDeliveryStatus(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test @DisplayName("wrong secret → 401")
    void wrongSecret_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/courier/webhook/delivery-status")
                .header("X-Webhook-Secret", "nope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
                .andExpect(status().isUnauthorized());
        verify(courierAdapter, never()).updateDeliveryStatus(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test @DisplayName("secret not configured (blank) → fails closed with 401")
    void blankSecret_failsClosed() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");
        mockMvc.perform(post("/api/v1/courier/webhook/delivery-status")
                .header("X-Webhook-Secret", "anything")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
                .andExpect(status().isUnauthorized());
    }
}
