package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.dto.GeocodingResponse;
import com.elcafe.modules.customer.service.GeocodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GeocodingControllerTest {
    private MockMvc mockMvc;
    @Mock private GeocodingService geocodingService;
    @InjectMocks private GeocodingController controller;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test @DisplayName("GET /reverse") void reverse() throws Exception {
        when(geocodingService.reverseGeocode(41.31, 69.24)).thenReturn(new GeocodingResponse());
        mockMvc.perform(get("/api/v1/customer/public/geocoding/reverse")
                .param("lat", "41.31").param("lon", "69.24")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /search") void search() throws Exception {
        when(geocodingService.searchLocations(eq("Tashkent"), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/customer/public/geocoding/search")
                .param("q", "Tashkent")).andExpect(status().isOk());
    }
}
