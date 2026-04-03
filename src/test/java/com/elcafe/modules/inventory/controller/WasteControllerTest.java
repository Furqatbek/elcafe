package com.elcafe.modules.inventory.controller;

import com.elcafe.modules.inventory.dto.WasteRecordRequest;
import com.elcafe.modules.inventory.dto.WasteRecordResponse;
import com.elcafe.modules.inventory.dto.WasteReportResponse;
import com.elcafe.modules.inventory.entity.WasteRecord;
import com.elcafe.modules.inventory.service.WasteService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WasteControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private WasteService wasteService;
    @InjectMocks private WasteController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test @DisplayName("GET / — lists waste records")
    void getWasteRecords() throws Exception {
        WasteRecordResponse response = WasteRecordResponse.builder().id(1L).build();
        when(wasteService.getWasteRecords(eq(1L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(response));
        mockMvc.perform(get("/api/v1/inventory/waste").param("restaurantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("GET /{id} — returns single waste record")
    void getById() throws Exception {
        WasteRecordResponse response = WasteRecordResponse.builder().id(1L).build();
        when(wasteService.getWasteRecordById(1L)).thenReturn(response);
        mockMvc.perform(get("/api/v1/inventory/waste/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("POST / — records waste")
    void recordWaste() throws Exception {
        WasteRecordRequest request = WasteRecordRequest.builder()
                .restaurantId(1L).ingredientId(1L)
                .quantity(new BigDecimal("5.0"))
                .wasteReason(WasteRecord.WasteReason.EXPIRED)
                .wasteDate(LocalDate.now())
                .recordedBy("admin").build();
        WasteRecordResponse response = WasteRecordResponse.builder().id(1L).build();
        when(wasteService.recordWaste(any(WasteRecordRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/inventory/waste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("DELETE /{id} — deletes waste record")
    void deleteWaste() throws Exception {
        mockMvc.perform(delete("/api/v1/inventory/waste/1"))
                .andExpect(status().isOk());
        verify(wasteService).deleteWasteRecord(1L);
    }

    @Test @DisplayName("GET /report — returns waste report")
    void getReport() throws Exception {
        WasteReportResponse report = WasteReportResponse.builder()
                .totalRecords(5).totalCost(new BigDecimal("50000")).build();
        when(wasteService.generateWasteReport(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(report);
        mockMvc.perform(get("/api/v1/inventory/waste/report")
                        .param("restaurantId", "1")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /reasons — returns waste reason options")
    void getReasons() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/waste/reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
