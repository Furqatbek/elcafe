package com.elcafe.modules.waiter.controller;

import com.elcafe.modules.waiter.dto.CommissionConfigRequest;
import com.elcafe.modules.waiter.dto.WaiterCommissionDTO;
import com.elcafe.modules.waiter.dto.WaiterCommissionSummaryDTO;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.enums.CommissionType;
import com.elcafe.modules.waiter.service.WaiterCommissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createWaiter;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WaiterCommissionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private WaiterCommissionService commissionService;
    @InjectMocks private WaiterCommissionController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("PUT /waiter/{id}/config - configures commission")
    void configureCommission_returns200() throws Exception {
        Waiter waiter = createWaiter();
        waiter.setCommissionEnabled(true);
        waiter.setCommissionPercent(BigDecimal.valueOf(10));

        CommissionConfigRequest request = new CommissionConfigRequest();
        request.setCommissionEnabled(true);
        request.setCommissionPercent(BigDecimal.valueOf(10));
        request.setCommissionType(CommissionType.PERCENTAGE);

        when(commissionService.updateCommissionConfig(eq(1L), any())).thenReturn(waiter);

        mockMvc.perform(put("/api/v1/waiter-commissions/waiter/1/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /waiter/{id}/summary - returns commission summary")
    void getCommissionSummary_returns200() throws Exception {
        WaiterCommissionSummaryDTO summary = WaiterCommissionSummaryDTO.builder()
                .waiterId(1L).waiterName("Ali")
                .totalCommissionEarned(BigDecimal.valueOf(50000))
                .build();

        when(commissionService.getCommissionSummary(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(summary);

        mockMvc.perform(get("/api/v1/waiter-commissions/waiter/1/summary")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-28"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /waiter/{id}/history - returns paginated history")
    void getCommissionHistory_returns200() throws Exception {
        WaiterCommissionDTO dto = WaiterCommissionDTO.builder()
                .id(1L).commissionAmount(BigDecimal.valueOf(5000)).build();
        Page<WaiterCommissionDTO> page = new PageImpl<>(List.of(dto));

        when(commissionService.getCommissionHistory(eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/waiter-commissions/waiter/1/history"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /restaurant/{id} - returns restaurant commissions")
    void getRestaurantCommissions_returns200() throws Exception {
        Page<WaiterCommissionDTO> page = new PageImpl<>(List.of());
        when(commissionService.getRestaurantCommissions(eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/waiter-commissions/restaurant/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /approve - approves commissions")
    void approveCommissions_returns200() throws Exception {
        when(commissionService.approveCommissions(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/waiter-commissions/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1, 2, 3]"))
                .andExpect(status().isOk());

        verify(commissionService).approveCommissions(List.of(1L, 2L, 3L));
    }
}
