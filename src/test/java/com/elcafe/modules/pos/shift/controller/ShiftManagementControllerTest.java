package com.elcafe.modules.pos.shift.controller;

import com.elcafe.modules.pos.shift.dto.*;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.entity.ShiftBreak;
import com.elcafe.modules.pos.shift.enums.BreakType;
import com.elcafe.modules.pos.shift.enums.ShiftStatus;
import com.elcafe.modules.pos.shift.service.ShiftManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ShiftManagementControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Mock private ShiftManagementService shiftService;
    @InjectMocks private ShiftManagementController controller;
    private final String BASE = "/api/v1/restaurants/1/pos/shifts";
    private EmployeeShift shift;
    private ShiftSummaryDTO summary;

    @BeforeEach
    void setUp() {
        ObjectMapper jacksonMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(jacksonMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter).build();
        shift = new EmployeeShift();
        shift.setId(1L);
        shift.setStatus(ShiftStatus.ACTIVE);
        shift.setClockIn(OffsetDateTime.now(ZoneOffset.UTC));
        shift.setShiftDate(LocalDate.now());
        shift.setBreakMinutes(0);
        shift.setBreaks(new java.util.ArrayList<>());
        summary = ShiftSummaryDTO.builder().id(1L).employeeId(1L).employeeName("Test")
                .status(ShiftStatus.ACTIVE).shiftDate(LocalDate.now()).build();
    }

    @Test @DisplayName("POST /{id}/break/start") void startBreak() throws Exception {
        ShiftBreak b = ShiftBreak.builder().id(1L)
                .breakStart(OffsetDateTime.now(ZoneOffset.UTC))
                .breakType(BreakType.MEAL).build();
        when(shiftService.startBreak(1L, BreakType.MEAL)).thenReturn(b);
        mockMvc.perform(post(BASE + "/1/break/start").param("breakType", "MEAL"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /active") void active() throws Exception {
        when(shiftService.getActiveShifts(1L)).thenReturn(List.of(summary));
        mockMvc.perform(get(BASE + "/active")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /date/{date}") void byDate() throws Exception {
        when(shiftService.getShiftsByDate(eq(1L), any())).thenReturn(List.of(summary));
        mockMvc.perform(get(BASE + "/date/2026-04-03")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /pending-approval") void pending() throws Exception {
        when(shiftService.getPendingApprovalShifts(1L)).thenReturn(List.of());
        mockMvc.perform(get(BASE + "/pending-approval")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /employees/{id}/history") void history() throws Exception {
        when(shiftService.getEmployeeShiftHistory(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));
        mockMvc.perform(get(BASE + "/employees/1/history")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /end-of-day/{date}") void eod() throws Exception {
        EndOfDayReport report = EndOfDayReport.builder().date(LocalDate.now()).totalShifts(1)
                .totalSales(BigDecimal.ZERO).totalOrders(0).shifts(List.of()).build();
        when(shiftService.getEndOfDayReport(eq(1L), any())).thenReturn(report);
        mockMvc.perform(get(BASE + "/end-of-day/2026-04-03")).andExpect(status().isOk());
    }
}
