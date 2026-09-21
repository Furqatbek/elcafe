package com.elcafe.modules.auth.controller;

import com.elcafe.modules.auth.dto.*;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.service.OperatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OperatorControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private OperatorService operatorService;
    @InjectMocks private OperatorController controller;
    private OperatorDTO dto;

    @BeforeEach void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        dto = OperatorDTO.builder().id(1L).email("op@test.com").firstName("Test")
                .lastName("Op").role(UserRole.OPERATOR).active(true).build();
    }

    @Test @DisplayName("GET / — paginated") void getAll() throws Exception {
        when(operatorService.getAllOperators(any())).thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));
        mockMvc.perform(get("/api/v1/operators")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(operatorService.getOperatorById(1L)).thenReturn(dto);
        mockMvc.perform(get("/api/v1/operators/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /") void create() throws Exception {
        CreateOperatorRequest req = new CreateOperatorRequest();
        req.setEmail("new@t.com"); req.setPassword("pass12345"); req.setFirstName("N"); req.setLastName("O");
        when(operatorService.createOperator(any())).thenReturn(dto);
        mockMvc.perform(post("/api/v1/operators").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("PUT /{id}") void update() throws Exception {
        UpdateOperatorRequest req = new UpdateOperatorRequest(); req.setFirstName("Updated");
        when(operatorService.updateOperator(eq(1L), any())).thenReturn(dto);
        mockMvc.perform(put("/api/v1/operators/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /{id}") void deleteOp() throws Exception {
        mockMvc.perform(delete("/api/v1/operators/1")).andExpect(status().isOk());
        verify(operatorService).deleteOperator(1L);
    }
}
