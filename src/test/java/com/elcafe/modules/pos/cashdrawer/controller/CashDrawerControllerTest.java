package com.elcafe.modules.pos.cashdrawer.controller;

import com.elcafe.modules.pos.cashdrawer.dto.CreateCashDrawerRequest;
import com.elcafe.modules.pos.cashdrawer.dto.DrawerCloseResult;
import com.elcafe.modules.pos.cashdrawer.dto.DrawerStatusResponse;
import com.elcafe.modules.pos.cashdrawer.dto.CashDrawerOperationDTO;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawer;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawerOperation;
import com.elcafe.modules.pos.cashdrawer.enums.CashOperationType;
import com.elcafe.modules.pos.cashdrawer.service.CashDrawerService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashDrawerControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CashDrawerService cashDrawerService;

    @InjectMocks
    private CashDrawerController controller;

    private CashDrawer drawer;
    private CashDrawerOperation operation;
    private final String BASE = "/api/v1/restaurants/1/pos/cash-drawers";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
        Restaurant r = new Restaurant();
        r.setId(1L);
        drawer = CashDrawer.builder().id(1L).restaurant(r).drawerName("Main").isActive(true)
                .expectedFloat(new BigDecimal("500000")).build();
        operation = CashDrawerOperation.builder().id(1L).cashDrawer(drawer)
                .operationType(CashOperationType.OPEN).amount(BigDecimal.ZERO).build();
    }

    @Test
    @DisplayName("POST / — create")
    void create() throws Exception {
        CreateCashDrawerRequest req = new CreateCashDrawerRequest();
        req.setDrawerName("Main");
        when(cashDrawerService.createCashDrawer(eq(1L), any(CreateCashDrawerRequest.class))).thenReturn(drawer);
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET / — list")
    void list() throws Exception {
        when(cashDrawerService.getCashDrawers(1L)).thenReturn(List.of(drawer));
        mockMvc.perform(get(BASE)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{id}/open")
    void open() throws Exception {
        when(cashDrawerService.openDrawer(eq(1L), eq(1L), isNull(), isNull())).thenReturn(operation);
        mockMvc.perform(post(BASE + "/1/open").param("operatorId", "1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{id}/paid-in")
    void paidIn() throws Exception {
        when(cashDrawerService.recordPaidIn(eq(1L), any(BigDecimal.class), eq(1L), isNull(), isNull()))
                .thenReturn(operation);
        mockMvc.perform(post(BASE + "/1/paid-in").param("amount", "100000").param("operatorId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{id}/paid-out")
    void paidOut() throws Exception {
        when(cashDrawerService.recordPaidOut(eq(1L), any(BigDecimal.class), eq(1L), isNull(), isNull()))
                .thenReturn(operation);
        mockMvc.perform(post(BASE + "/1/paid-out").param("amount", "50000").param("operatorId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{id}/drop")
    void drop() throws Exception {
        when(cashDrawerService.recordCashDrop(eq(1L), any(BigDecimal.class), eq(1L), isNull(), isNull()))
                .thenReturn(operation);
        mockMvc.perform(post(BASE + "/1/drop").param("amount", "200000").param("operatorId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{id}/pickup")
    void pickup() throws Exception {
        when(cashDrawerService.recordCashPickup(eq(1L), any(BigDecimal.class), eq(1L), isNull(), isNull()))
                .thenReturn(operation);
        mockMvc.perform(post(BASE + "/1/pickup").param("amount", "100000").param("operatorId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{id}/close")
    void close() throws Exception {
        DrawerCloseResult result = DrawerCloseResult.builder().drawerId(1L)
                .countedAmount(new BigDecimal("700000")).expectedAmount(new BigDecimal("700000"))
                .variance(BigDecimal.ZERO).build();
        when(cashDrawerService.closeDrawer(eq(1L), eq(1L), isNull(), any(BigDecimal.class))).thenReturn(result);
        mockMvc.perform(post(BASE + "/1/close").param("operatorId", "1").param("countedAmount", "700000"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id}/status")
    void getStatus() throws Exception {
        DrawerStatusResponse resp = DrawerStatusResponse.builder().drawerId(1L).drawerName("Main")
                .currentExpectedCash(new BigDecimal("500000")).recentOperations(List.of()).build();
        when(cashDrawerService.getDrawerStatus(eq(1L), isNull())).thenReturn(resp);
        mockMvc.perform(get(BASE + "/1/status")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id}/history")
    void history() throws Exception {
        when(cashDrawerService.getOperationHistory(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(get(BASE + "/1/history")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /shifts/{id}/operations")
    void shiftOps() throws Exception {
        when(cashDrawerService.getShiftOperations(1L)).thenReturn(List.of());
        mockMvc.perform(get(BASE + "/shifts/1/operations")).andExpect(status().isOk());
    }
}
