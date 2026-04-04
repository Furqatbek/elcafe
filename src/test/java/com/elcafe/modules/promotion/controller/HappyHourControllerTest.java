package com.elcafe.modules.promotion.controller;

import com.elcafe.modules.promotion.dto.ActiveHappyHourResponse;
import com.elcafe.modules.promotion.dto.HappyHourRequest;
import com.elcafe.modules.promotion.dto.HappyHourResponse;
import com.elcafe.modules.promotion.service.HappyHourService;
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
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class HappyHourControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private HappyHourService happyHourService;
    @InjectMocks private HappyHourController controller;
    private HappyHourResponse response;

    @BeforeEach void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
        response = HappyHourResponse.builder().id(1L).name("Evening").discountPercent(new BigDecimal("20"))
                .active(true).schedules(List.of()).productTargets(List.of()).build();
    }

    @Test @DisplayName("GET /restaurants/{id}/happy-hours") void list() throws Exception {
        when(happyHourService.getHappyHoursByRestaurant(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/v1/restaurants/1/happy-hours")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /happy-hours/{id}") void getById() throws Exception {
        when(happyHourService.getHappyHour(1L)).thenReturn(response);
        mockMvc.perform(get("/api/v1/happy-hours/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /restaurants/{id}/happy-hours") void create() throws Exception {
        HappyHourRequest req = new HappyHourRequest(); req.setName("New"); req.setDiscountPercent(new BigDecimal("15"));
        when(happyHourService.createHappyHour(eq(1L), any())).thenReturn(response);
        mockMvc.perform(post("/api/v1/restaurants/1/happy-hours").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("PUT /happy-hours/{id}") void update() throws Exception {
        HappyHourRequest req = new HappyHourRequest(); req.setName("Updated");
        when(happyHourService.updateHappyHour(eq(1L), any())).thenReturn(response);
        mockMvc.perform(put("/api/v1/happy-hours/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /happy-hours/{id}") void deleteHH() throws Exception {
        mockMvc.perform(delete("/api/v1/happy-hours/1")).andExpect(status().isOk());
        verify(happyHourService).deleteHappyHour(1L);
    }
    @Test @DisplayName("PATCH /happy-hours/{id}/toggle") void toggle() throws Exception {
        when(happyHourService.toggleHappyHour(1L)).thenReturn(response);
        mockMvc.perform(patch("/api/v1/happy-hours/1/toggle")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /restaurants/{id}/happy-hours/active") void getActive() throws Exception {
        when(happyHourService.getActiveHappyHour(1L)).thenReturn(Optional.of(
                ActiveHappyHourResponse.builder().id(1L).name("Evening").discountPercent(new BigDecimal("20")).build()));
        mockMvc.perform(get("/api/v1/restaurants/1/happy-hours/active")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /restaurants/{id}/happy-hours/is-active") void isActive() throws Exception {
        when(happyHourService.isHappyHourActive(1L)).thenReturn(true);
        mockMvc.perform(get("/api/v1/restaurants/1/happy-hours/is-active")).andExpect(status().isOk());
    }
}
