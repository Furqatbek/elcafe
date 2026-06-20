package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.inventory.dto.GeneratePORequest;
import com.elcafe.modules.inventory.dto.POSuggestionResponse;
import com.elcafe.modules.inventory.service.POSuggestionService;
import com.elcafe.modules.financial.entity.PurchaseOrder;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class POSuggestionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private POSuggestionService poSuggestionService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private POSuggestionController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test @DisplayName("GET / — returns suggestions grouped by supplier")
    void getSuggestions() throws Exception {
        POSuggestionResponse suggestion = POSuggestionResponse.builder()
                .supplierId(1L).supplierName("Fresh Foods")
                .urgency(POSuggestionResponse.Urgency.HIGH)
                .estimatedTotal(BigDecimal.valueOf(500000)).build();
        when(poSuggestionService.getSuggestions(1L)).thenReturn(List.of(suggestion));

        mockMvc.perform(get("/api/v1/inventory/po-suggestions").param("restaurantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("GET /count — returns suggestion count")
    void getCount() throws Exception {
        when(poSuggestionService.getSuggestionCount(1L)).thenReturn(3);
        mockMvc.perform(get("/api/v1/inventory/po-suggestions/count").param("restaurantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("POST /generate — generates PO from suggestion")
    void generate() throws Exception {
        GeneratePORequest request = GeneratePORequest.builder()
                .restaurantId(1L).supplierId(1L).build();
        PurchaseOrder po = new PurchaseOrder();
        po.setId(10L);
        po.setPoNumber("PO-2026-001");
        when(poSuggestionService.generatePurchaseOrder(any(GeneratePORequest.class), anyString()))
                .thenReturn(po);

        mockMvc.perform(post("/api/v1/inventory/po-suggestions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("POST /generate-all — generates all POs")
    void generateAll() throws Exception {
        when(poSuggestionService.getSuggestions(1L)).thenReturn(List.of());
        mockMvc.perform(post("/api/v1/inventory/po-suggestions/generate-all")
                        .param("restaurantId", "1"))
                .andExpect(status().isCreated());
    }
}
