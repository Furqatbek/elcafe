package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.dto.CustomerActivityDTO;
import com.elcafe.modules.customer.dto.CustomerActivityFilterDTO;
import com.elcafe.modules.customer.service.CustomerActivityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CustomerActivityControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Mock private CustomerActivityService customerActivityService;
    @InjectMocks private CustomerActivityController controller;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test @DisplayName("GET /") void getAll() throws Exception {
        when(customerActivityService.getAllCustomersActivity()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/customers/activity")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /filter") void getFiltered() throws Exception {
        when(customerActivityService.getFilteredCustomersActivity(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/customers/activity/filter")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /filter") void postFilter() throws Exception {
        when(customerActivityService.getFilteredCustomersActivity(any())).thenReturn(List.of());
        mockMvc.perform(post("/api/v1/customers/activity/filter").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CustomerActivityFilterDTO())))
                .andExpect(status().isOk());
    }
}
