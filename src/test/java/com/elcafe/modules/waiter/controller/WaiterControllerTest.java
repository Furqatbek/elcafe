package com.elcafe.modules.waiter.controller;

import com.elcafe.modules.waiter.dto.WaiterAuthRequest;
import com.elcafe.modules.waiter.dto.WaiterAuthResponse;
import com.elcafe.modules.waiter.dto.WaiterResponse;
import com.elcafe.modules.waiter.enums.WaiterRole;
import com.elcafe.modules.waiter.service.WaiterService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WaiterControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private WaiterService waiterService;
    @InjectMocks private WaiterController waiterController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(waiterController).build();
    }

    @Test @DisplayName("POST /auth") void authenticate() throws Exception {
        when(waiterService.authenticate(any())).thenReturn(
                WaiterAuthResponse.builder().waiterId(1L).name("Ali").token("jwt").build());
        WaiterAuthRequest req = new WaiterAuthRequest(); req.setPinCode("1234");
        mockMvc.perform(post("/api/v1/waiters/auth").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /active") void getActive() throws Exception {
        when(waiterService.getActiveWaiters()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/waiters/active")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(waiterService.getById(1L)).thenReturn(WaiterResponse.builder().id(1L).name("Ali")
                .role(WaiterRole.WAITER).active(true).activeTablesCount(0).build());
        mockMvc.perform(get("/api/v1/waiters/1")).andExpect(status().isOk());
    }
}
