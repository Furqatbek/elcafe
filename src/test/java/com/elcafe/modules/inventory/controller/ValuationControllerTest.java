package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.inventory.dto.ValuationReportDTO.CostVarianceReport;
import com.elcafe.modules.inventory.dto.ValuationReportDTO.InventoryValuationReport;
import com.elcafe.modules.inventory.dto.ValuationReportDTO.ValuationComparisonReport;
import com.elcafe.modules.inventory.entity.BatchConsumption;
import com.elcafe.modules.inventory.entity.IngredientCostHistory;
import com.elcafe.modules.inventory.entity.ValuationSettings;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.service.BatchConsumptionService;
import com.elcafe.modules.inventory.service.CostHistoryService;
import com.elcafe.modules.inventory.service.InventoryValuationService;
import com.elcafe.modules.inventory.service.ValuationReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValuationControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private InventoryValuationService valuationService;
    @Mock private CostHistoryService costHistoryService;
    @Mock private BatchConsumptionService consumptionService;
    @Mock private ValuationReportService reportService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private ValuationController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Nested @DisplayName("Valuation Settings")
    class SettingsTests {

        @Test @DisplayName("GET /settings — returns valuation method")
        void getSettings() throws Exception {
            when(valuationService.getValuationMethod(1L)).thenReturn(ValuationMethod.WEIGHTED_AVERAGE);
            mockMvc.perform(get("/api/v1/inventory/valuation/settings").param("restaurantId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valuationMethod").value("WEIGHTED_AVERAGE"));
        }

        @Test @DisplayName("POST /settings — sets valuation method")
        void setSettings() throws Exception {
            ValuationSettings settings = new ValuationSettings();
            settings.setId(1L);
            settings.setValuationMethod(ValuationMethod.FIFO);
            settings.setEffectiveFrom(java.time.LocalDate.now());
            settings.setIsActive(true);
            when(valuationService.setValuationMethod(eq(1L), eq(ValuationMethod.FIFO), eq("admin")))
                    .thenReturn(settings);

            String body = objectMapper.writeValueAsString(
                    new ValuationController.SetValuationMethodRequest(1L, ValuationMethod.FIFO, "admin"));

            mockMvc.perform(post("/api/v1/inventory/valuation/settings")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("Inventory Valuation")
    class ValuationTests {

        @Test @DisplayName("GET /calculate — calculates inventory value")
        void calculate() throws Exception {
            when(valuationService.getValuationMethod(1L)).thenReturn(ValuationMethod.WEIGHTED_AVERAGE);
            when(valuationService.calculateInventoryValue(eq(1L), any(ValuationMethod.class)))
                    .thenReturn(new InventoryValuationService.InventoryValuation(
                            1L, ValuationMethod.WEIGHTED_AVERAGE, new BigDecimal("5000000"),
                            List.of(), LocalDateTime.now()));

            mockMvc.perform(get("/api/v1/inventory/valuation/calculate").param("restaurantId", "1"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("GET /compare — compares FIFO/LIFO/WAC")
        void compare() throws Exception {
            when(valuationService.calculateInventoryValue(eq(1L), eq(ValuationMethod.FIFO)))
                    .thenReturn(new InventoryValuationService.InventoryValuation(
                            1L, ValuationMethod.FIFO, new BigDecimal("4800000"),
                            List.of(), LocalDateTime.now()));
            when(valuationService.calculateInventoryValue(eq(1L), eq(ValuationMethod.LIFO)))
                    .thenReturn(new InventoryValuationService.InventoryValuation(
                            1L, ValuationMethod.LIFO, new BigDecimal("5200000"),
                            List.of(), LocalDateTime.now()));
            when(valuationService.calculateInventoryValue(eq(1L), eq(ValuationMethod.WEIGHTED_AVERAGE)))
                    .thenReturn(new InventoryValuationService.InventoryValuation(
                            1L, ValuationMethod.WEIGHTED_AVERAGE, new BigDecimal("5000000"),
                            List.of(), LocalDateTime.now()));

            mockMvc.perform(get("/api/v1/inventory/valuation/compare").param("restaurantId", "1"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("GET /ingredient/{id} — returns ingredient valuation")
        void ingredientValuation() throws Exception {
            when(valuationService.calculateIngredientValue(1L, ValuationMethod.WEIGHTED_AVERAGE))
                    .thenReturn(new BigDecimal("500000"));
            mockMvc.perform(get("/api/v1/inventory/valuation/ingredient/1"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("POST /ingredient/{id}/recalculate-wac — recalculates WAC")
        void recalculateWAC() throws Exception {
            when(valuationService.recalculateWAC(1L)).thenReturn(new BigDecimal("4500"));
            mockMvc.perform(post("/api/v1/inventory/valuation/ingredient/1/recalculate-wac"))
                    .andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("Cost History")
    class CostHistoryTests {

        @Test @DisplayName("GET /cost-history/{id} — returns cost history")
        void getCostHistory() throws Exception {
            when(costHistoryService.getCostHistory(1L)).thenReturn(List.of());
            mockMvc.perform(get("/api/v1/inventory/valuation/cost-history/1"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("GET /cost-history/{id}/paginated — returns paginated history")
        void getPaginated() throws Exception {
            when(costHistoryService.getCostHistoryPaginated(1L, 0, 20))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
            mockMvc.perform(get("/api/v1/inventory/valuation/cost-history/1/paginated"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("GET /cost-history/{id}/range — returns range history")
        void getRange() throws Exception {
            when(costHistoryService.getCostChangesInRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());
            mockMvc.perform(get("/api/v1/inventory/valuation/cost-history/1/range")
                            .param("startDate", "2026-01-01T00:00:00")
                            .param("endDate", "2026-03-31T23:59:59"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("POST /cost-history/{id} — records cost change")
        void recordCostChange() throws Exception {
            IngredientCostHistory history = IngredientCostHistory.builder().id(1L)
                    .previousCost(new BigDecimal("5000")).newCost(new BigDecimal("6000")).build();
            when(costHistoryService.recordCostChange(eq(1L), any(BigDecimal.class), any(), eq("admin")))
                    .thenReturn(history);

            String body = objectMapper.writeValueAsString(
                    new ValuationController.RecordCostChangeRequest(new BigDecimal("6000"), "admin", "price update"));

            mockMvc.perform(post("/api/v1/inventory/valuation/cost-history/1")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("GET /cost-variance/{id} — returns cost variance")
        void getCostVariance() throws Exception {
            when(costHistoryService.calculateCostVariance(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(new CostHistoryService.CostVariance(1L, "Flour",
                            new BigDecimal("5000"), new BigDecimal("4000"),
                            new BigDecimal("1000"), new BigDecimal("25")));
            mockMvc.perform(get("/api/v1/inventory/valuation/cost-variance/1")
                            .param("startDate", "2026-01-01T00:00:00")
                            .param("endDate", "2026-03-31T23:59:59"))
                    .andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("Consumption")
    class ConsumptionTests {

        @Test @DisplayName("GET /consumption/{id} — returns consumption history")
        void getHistory() throws Exception {
            when(consumptionService.getConsumptionHistory(1L)).thenReturn(List.of());
            mockMvc.perform(get("/api/v1/inventory/valuation/consumption/1"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("GET /consumption/summary — returns consumption summary")
        void getSummary() throws Exception {
            when(consumptionService.getConsumptionByIngredient(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());
            mockMvc.perform(get("/api/v1/inventory/valuation/consumption/summary")
                            .param("restaurantId", "1")
                            .param("startDate", "2026-01-01T00:00:00")
                            .param("endDate", "2026-03-31T23:59:59"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("GET /consumption/{id}/stats — returns consumption stats")
        void getStats() throws Exception {
            when(consumptionService.getConsumptionStats(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(new BatchConsumptionService.ConsumptionStats(
                            1L, "Flour", new BigDecimal("100"), new BigDecimal("500000"),
                            new BigDecimal("5000"), new BigDecimal("4500"), new BigDecimal("5500"), 10));
            mockMvc.perform(get("/api/v1/inventory/valuation/consumption/1/stats")
                            .param("startDate", "2026-01-01T00:00:00")
                            .param("endDate", "2026-03-31T23:59:59"))
                    .andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("COGS")
    class COGSTests {

        @Test @DisplayName("GET /cogs/order/{orderId} — returns order COGS")
        void orderCOGS() throws Exception {
            when(consumptionService.calculateOrderCOGS(1L)).thenReturn(new BigDecimal("150000"));
            when(consumptionService.getConsumptionsForOrder(1L)).thenReturn(List.of());
            mockMvc.perform(get("/api/v1/inventory/valuation/cogs/order/1"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("GET /cogs/total — returns total COGS for period")
        void totalCOGS() throws Exception {
            when(consumptionService.calculateTotalCOGS(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(new BigDecimal("5000000"));
            mockMvc.perform(get("/api/v1/inventory/valuation/cogs/total")
                            .param("restaurantId", "1")
                            .param("startDate", "2026-01-01T00:00:00")
                            .param("endDate", "2026-03-31T23:59:59"))
                    .andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("Reports")
    class ReportTests {

        @Test @DisplayName("GET /reports/comparison — returns comparison report")
        void comparisonReport() throws Exception {
            when(reportService.generateComparisonReport(1L)).thenReturn(new ValuationComparisonReport());
            mockMvc.perform(get("/api/v1/inventory/valuation/reports/comparison")
                            .param("restaurantId", "1"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("GET /reports/inventory — returns inventory report")
        void inventoryReport() throws Exception {
            when(reportService.generateValuationReport(eq(1L), any())).thenReturn(new InventoryValuationReport());
            mockMvc.perform(get("/api/v1/inventory/valuation/reports/inventory")
                            .param("restaurantId", "1"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("GET /reports/variance — returns variance report")
        void varianceReport() throws Exception {
            when(reportService.generateCostVarianceReport(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(new CostVarianceReport());
            mockMvc.perform(get("/api/v1/inventory/valuation/reports/variance")
                            .param("restaurantId", "1")
                            .param("startDate", "2026-01-01T00:00:00")
                            .param("endDate", "2026-03-31T23:59:59"))
                    .andExpect(status().isOk());
        }
    }
}
