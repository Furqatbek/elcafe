package com.elcafe.modules.financial.controller;

import com.elcafe.modules.financial.service.FinancialReportsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FinancialReportsControllerTest {

    private MockMvc mockMvc;
    @Mock private FinancialReportsService reportsService;
    @InjectMocks private FinancialReportsController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test @DisplayName("GET /profit-loss") void profitLoss() throws Exception {
        when(reportsService.generateProfitLossReport(anyLong(), any(), any()))
                .thenReturn(new FinancialReportsService.ProfitLossReport());
        mockMvc.perform(get("/api/v1/financial/reports/profit-loss")
                .param("restaurantId", "1").param("startDate", "2026-03-01").param("endDate", "2026-03-31"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /balance-sheet") void balanceSheet() throws Exception {
        when(reportsService.generateBalanceSheet(anyLong(), any()))
                .thenReturn(new FinancialReportsService.BalanceSheetReport());
        mockMvc.perform(get("/api/v1/financial/reports/balance-sheet")
                .param("restaurantId", "1").param("asOfDate", "2026-03-31"))
                .andExpect(status().isOk());
    }
}
