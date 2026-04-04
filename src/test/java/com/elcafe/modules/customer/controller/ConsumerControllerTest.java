package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.dto.UpdateConsumerProfileRequest;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.service.CustomerService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConsumerControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private CustomerService customerService;
    @InjectMocks private ConsumerController controller;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test @DisplayName("GET /profile") void getProfile() throws Exception {
        // This endpoint uses @AuthenticationPrincipal — will get null in standalone MockMvc
        // but the controller handles null customerId gracefully or we test the endpoint mapping
        mockMvc.perform(get("/api/v1/consumer/profile")).andExpect(status().isOk());
    }
    @Test @DisplayName("PUT /profile") void updateProfile() throws Exception {
        UpdateConsumerProfileRequest req = new UpdateConsumerProfileRequest(); req.setFirstName("Updated");
        when(customerService.updateConsumerProfile(anyLong(), any())).thenReturn(
                Customer.builder().id(1L).firstName("Updated").build());
        mockMvc.perform(put("/api/v1/consumer/profile").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
}
