package com.elcafe.modules.waiter.controller;

import com.elcafe.modules.waiter.dto.WaiterCommissionSummaryDTO;
import com.elcafe.modules.waiter.service.WaiterCommissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WaiterCommissionControllerTest {

    private MockMvc mockMvc;
    @Mock private WaiterCommissionService commissionService;
    @InjectMocks private WaiterCommissionController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test @DisplayName("GET /waiter/{id}/summary") void getSummary() throws Exception {
        when(commissionService.getCommissionSummary(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(WaiterCommissionSummaryDTO.builder().waiterId(1L)
                        .totalCommissionEarned(BigDecimal.ZERO).build());
        mockMvc.perform(get("/api/v1/waiter-commissions/waiter/1/summary")
                .param("startDate", "2026-03-01").param("endDate", "2026-03-31"))
                .andExpect(status().isOk());
    }
}
