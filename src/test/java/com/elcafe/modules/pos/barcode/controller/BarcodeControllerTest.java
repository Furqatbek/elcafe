package com.elcafe.modules.pos.barcode.controller;

import com.elcafe.modules.pos.barcode.dto.BarcodeLookupResult;
import com.elcafe.modules.pos.barcode.service.BarcodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BarcodeControllerTest {
    private MockMvc mockMvc;
    @Mock private BarcodeService barcodeService;
    @InjectMocks private BarcodeController controller;

    @BeforeEach
    void setUp() { mockMvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test @DisplayName("GET /lookup/{code}") void lookup() throws Exception {
        when(barcodeService.lookup(1L, "123")).thenReturn(BarcodeLookupResult.notFound("123"));
        mockMvc.perform(get("/api/v1/restaurants/1/pos/barcode/lookup/123")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /exists/{code}") void exists() throws Exception {
        when(barcodeService.exists(1L, "123")).thenReturn(true);
        mockMvc.perform(get("/api/v1/restaurants/1/pos/barcode/exists/123")).andExpect(status().isOk());
    }
}
