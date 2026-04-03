package com.elcafe.modules.inventory.controller;

import com.elcafe.modules.inventory.dto.SupplierRequest;
import com.elcafe.modules.inventory.dto.SupplierResponse;
import com.elcafe.modules.inventory.service.SupplierService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SupplierControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private SupplierService supplierService;
    @InjectMocks private SupplierController controller;

    private SupplierResponse supplierResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        supplierResponse = SupplierResponse.builder()
                .id(1L).restaurantId(1L).name("Fresh Foods").code("FF001").active(true).build();
    }

    @Test @DisplayName("GET / — activeOnly=false returns all")
    void getAll() throws Exception {
        when(supplierService.getAllByRestaurant(1L)).thenReturn(List.of(supplierResponse));
        mockMvc.perform(get("/api/v1/inventory/suppliers").param("restaurantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("GET / — activeOnly=true returns active")
    void getActive() throws Exception {
        when(supplierService.getActiveByRestaurant(1L)).thenReturn(List.of(supplierResponse));
        mockMvc.perform(get("/api/v1/inventory/suppliers")
                        .param("restaurantId", "1").param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("GET /{id} — returns supplier")
    void getById() throws Exception {
        when(supplierService.getById(1L)).thenReturn(supplierResponse);
        mockMvc.perform(get("/api/v1/inventory/suppliers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Fresh Foods"));
    }

    @Test @DisplayName("POST / — creates supplier")
    void create() throws Exception {
        SupplierRequest request = SupplierRequest.builder()
                .restaurantId(1L).name("Fresh Foods").code("FF001").build();
        when(supplierService.create(any(SupplierRequest.class))).thenReturn(supplierResponse);

        mockMvc.perform(post("/api/v1/inventory/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Fresh Foods"));
    }

    @Test @DisplayName("PUT /{id} — updates supplier")
    void update() throws Exception {
        SupplierRequest request = SupplierRequest.builder()
                .restaurantId(1L).name("Updated Foods").build();
        when(supplierService.update(eq(1L), any(SupplierRequest.class))).thenReturn(supplierResponse);

        mockMvc.perform(put("/api/v1/inventory/suppliers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /{id} — soft deletes")
    void deleteSupplier() throws Exception {
        mockMvc.perform(delete("/api/v1/inventory/suppliers/1"))
                .andExpect(status().isOk());
        verify(supplierService).delete(1L);
    }

    @Test @DisplayName("POST /{id}/toggle — toggles active")
    void toggleActive() throws Exception {
        when(supplierService.toggleActive(1L)).thenReturn(supplierResponse);
        mockMvc.perform(post("/api/v1/inventory/suppliers/1/toggle"))
                .andExpect(status().isOk());
    }
}
