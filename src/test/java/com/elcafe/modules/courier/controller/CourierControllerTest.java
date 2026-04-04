package com.elcafe.modules.courier.controller;

import com.elcafe.modules.courier.dto.*;
import com.elcafe.modules.courier.enums.CourierStatus;
import com.elcafe.modules.courier.enums.CourierType;
import com.elcafe.modules.courier.enums.CourierVehicle;
import com.elcafe.modules.courier.service.CourierService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CourierControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private CourierService courierService;
    @InjectMocks private CourierController controller;
    private CourierDTO dto;

    @BeforeEach void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        dto = CourierDTO.builder().id(1L).email("c@t.com").firstName("Test").lastName("Courier")
                .courierType(CourierType.FULL_TIME).vehicle(CourierVehicle.MOTORCYCLE).build();
    }

    @Test @DisplayName("GET /") void getAll() throws Exception {
        when(courierService.getAllCouriers(any())).thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));
        mockMvc.perform(get("/api/v1/couriers")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(courierService.getCourierById(1L)).thenReturn(dto);
        mockMvc.perform(get("/api/v1/couriers/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}/wallet") void getWallet() throws Exception {
        when(courierService.getCourierWallet(1L)).thenReturn(CourierWalletDTO.builder().id(1L).balance(new BigDecimal("50000")).build());
        mockMvc.perform(get("/api/v1/couriers/1/wallet")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /") void create() throws Exception {
        CreateCourierRequest req = new CreateCourierRequest();
        req.setEmail("new@t.com"); req.setPassword("pass123"); req.setFirstName("N"); req.setLastName("C");
        req.setCourierType(CourierType.FULL_TIME); req.setVehicle(CourierVehicle.MOTORCYCLE);
        when(courierService.createCourier(any())).thenReturn(dto);
        mockMvc.perform(post("/api/v1/couriers").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("PUT /{id}") void update() throws Exception {
        UpdateCourierRequest req = new UpdateCourierRequest(); req.setFirstName("Updated");
        when(courierService.updateCourier(eq(1L), any())).thenReturn(dto);
        mockMvc.perform(put("/api/v1/couriers/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /{id}") void deleteC() throws Exception {
        mockMvc.perform(delete("/api/v1/couriers/1")).andExpect(status().isOk());
        verify(courierService).deleteCourier(1L);
    }
    @Test @DisplayName("POST /{id}/status") void updateStatus() throws Exception {
        CourierStatusUpdateRequest req = new CourierStatusUpdateRequest(); req.setStatus(CourierStatus.ONLINE);
        when(courierService.updateCourierStatus(eq(1L), any())).thenReturn(
                CourierStatusResponse.builder().courierId(1L).isOnline(true).currentStatus(CourierStatus.ONLINE).build());
        mockMvc.perform(post("/api/v1/couriers/1/status").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}/status") void getStatus() throws Exception {
        when(courierService.getCourierStatus(1L)).thenReturn(
                CourierStatusResponse.builder().courierId(1L).isOnline(true).build());
        mockMvc.perform(get("/api/v1/couriers/1/status")).andExpect(status().isOk());
    }
}
