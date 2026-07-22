package com.elcafe.modules.courier.controller;

import com.elcafe.modules.courier.dto.CourierTariffResponse;
import com.elcafe.modules.courier.dto.CreateCourierTariffRequest;
import com.elcafe.modules.courier.dto.UpdateCourierTariffRequest;
import com.elcafe.modules.courier.enums.TariffType;
import com.elcafe.modules.courier.service.CourierTariffService;
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
class CourierTariffControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private CourierTariffService courierTariffService;
    @InjectMocks private CourierTariffController controller;
    private CourierTariffResponse resp;

    @BeforeEach void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        resp = CourierTariffResponse.builder().id(1L).name("Standard").type(TariffType.BONUS)
                .fixedAmount(new BigDecimal("10000")).active(true).build();
    }

    @Test @DisplayName("GET /") void getAll() throws Exception {
        when(courierTariffService.getAllTariffs(any())).thenReturn(new PageImpl<>(List.of(resp), PageRequest.of(0, 10), 1));
        mockMvc.perform(get("/api/v1/couriers/tariffs")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /active") void getActive() throws Exception {
        when(courierTariffService.getActiveTariffs()).thenReturn(List.of(resp));
        mockMvc.perform(get("/api/v1/couriers/tariffs/active")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(courierTariffService.getTariffById(1L)).thenReturn(resp);
        mockMvc.perform(get("/api/v1/couriers/tariffs/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /") void create() throws Exception {
        CreateCourierTariffRequest req = new CreateCourierTariffRequest();
        req.setName("New"); req.setType(TariffType.BONUS); req.setFixedAmount(new BigDecimal("5000"));
        when(courierTariffService.createTariff(any())).thenReturn(resp);
        mockMvc.perform(post("/api/v1/couriers/tariffs").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("PUT /{id}") void update() throws Exception {
        UpdateCourierTariffRequest req = new UpdateCourierTariffRequest(); req.setFixedAmount(new BigDecimal("15000"));
        when(courierTariffService.updateTariff(eq(1L), any())).thenReturn(resp);
        mockMvc.perform(put("/api/v1/couriers/tariffs/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /{id}") void deleteT() throws Exception {
        mockMvc.perform(delete("/api/v1/couriers/tariffs/1")).andExpect(status().isOk());
        verify(courierTariffService).deleteTariff(1L);
    }
}
