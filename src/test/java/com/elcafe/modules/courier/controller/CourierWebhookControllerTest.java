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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourierWebhookControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private LocalCourierAdapter courierAdapter;
    @InjectMocks private CourierWebhookController controller;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test @DisplayName("POST /delivery-status — processes webhook")
    void deliveryStatus() throws Exception {
        mockMvc.perform(post("/api/v1/courier/webhook/delivery-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("trackingId", "TRACK-001", "status", "DELIVERED"))))
                .andExpect(status().isOk());
        verify(courierAdapter).updateDeliveryStatus("TRACK-001", "DELIVERED");
    }
}
