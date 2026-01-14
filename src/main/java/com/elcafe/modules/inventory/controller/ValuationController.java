package com.elcafe.modules.inventory.controller;

import com.elcafe.modules.inventory.dto.ValuationReportDTO.*;
import com.elcafe.modules.inventory.entity.BatchConsumption;
import com.elcafe.modules.inventory.entity.IngredientCostHistory;
import com.elcafe.modules.inventory.entity.ValuationSettings;
import com.elcafe.modules.inventory.enums.CostChangeReason;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.service.BatchConsumptionService;
import com.elcafe.modules.inventory.service.CostHistoryService;
import com.elcafe.modules.inventory.service.InventoryValuationService;
import com.elcafe.modules.inventory.service.ValuationReportService;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for inventory valuation operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/inventory/valuation")
@RequiredArgsConstructor
public class ValuationController {

    private final InventoryValuationService valuationService;
    private final CostHistoryService costHistoryService;
    private final BatchConsumptionService consumptionService;
    private final ValuationReportService reportService;

    // ==================== Valuation Settings ====================

    /**
     * Get current valuation method for a restaurant
     */
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<ValuationMethodResponse>> getValuationMethod(
            @RequestParam Long restaurantId) {
        log.info("Getting valuation method for restaurant: {}", restaurantId);

        ValuationMethod method = valuationService.getValuationMethod(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Valuation method retrieved",
                new ValuationMethodResponse(restaurantId, method)));
    }

    /**
     * Set valuation method for a restaurant
     */
    @PostMapping("/settings")
    public ResponseEntity<ApiResponse<ValuationSettings>> setValuationMethod(
            @RequestBody SetValuationMethodRequest request) {
        log.info("Setting valuation method for restaurant {} to {}",
                request.restaurantId, request.valuationMethod);

        ValuationSettings settings = valuationService.setValuationMethod(
                request.restaurantId, request.valuationMethod, request.createdBy);

        return ResponseEntity.ok(ApiResponse.success("Valuation method updated", settings));
    }

    // ==================== Inventory Valuation ====================

    /**
     * Calculate inventory value for a restaurant
     */
    @GetMapping("/calculate")
    public ResponseEntity<ApiResponse<InventoryValuationService.InventoryValuation>> calculateInventoryValue(
            @RequestParam Long restaurantId,
            @RequestParam(required = false) ValuationMethod method) {
        log.info("Calculating inventory value for restaurant {} using method {}",
                restaurantId, method);

        ValuationMethod useMethod = method != null ? method : valuationService.getValuationMethod(restaurantId);
        InventoryValuationService.InventoryValuation valuation =
                valuationService.calculateInventoryValue(restaurantId, useMethod);

        return ResponseEntity.ok(ApiResponse.success("Inventory value calculated", valuation));
    }

    /**
     * Compare inventory value across different methods
     */
    @GetMapping("/compare")
    public ResponseEntity<ApiResponse<ValuationComparison>> compareValuationMethods(
            @RequestParam Long restaurantId) {
        log.info("Comparing valuation methods for restaurant {}", restaurantId);

        BigDecimal fifoValue = valuationService.calculateInventoryValue(restaurantId, ValuationMethod.FIFO).totalValue();
        BigDecimal lifoValue = valuationService.calculateInventoryValue(restaurantId, ValuationMethod.LIFO).totalValue();
        BigDecimal wacValue = valuationService.calculateInventoryValue(restaurantId, ValuationMethod.WEIGHTED_AVERAGE).totalValue();

        ValuationComparison comparison = new ValuationComparison(
                restaurantId, fifoValue, lifoValue, wacValue, LocalDateTime.now());

        return ResponseEntity.ok(ApiResponse.success("Valuation comparison completed", comparison));
    }

    /**
     * Calculate value for a specific ingredient
     */
    @GetMapping("/ingredient/{ingredientId}")
    public ResponseEntity<ApiResponse<IngredientValuationResponse>> getIngredientValuation(
            @PathVariable Long ingredientId,
            @RequestParam(required = false) ValuationMethod method) {
        log.info("Getting valuation for ingredient {} using method {}", ingredientId, method);

        ValuationMethod useMethod = method != null ? method : ValuationMethod.WEIGHTED_AVERAGE;
        BigDecimal value = valuationService.calculateIngredientValue(ingredientId, useMethod);

        return ResponseEntity.ok(ApiResponse.success("Ingredient valuation calculated",
                new IngredientValuationResponse(ingredientId, useMethod, value)));
    }

    /**
     * Recalculate weighted average cost for an ingredient
     */
    @PostMapping("/ingredient/{ingredientId}/recalculate-wac")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recalculateWAC(
            @PathVariable Long ingredientId) {
        log.info("Recalculating WAC for ingredient {}", ingredientId);

        BigDecimal wac = valuationService.recalculateWAC(ingredientId);

        return ResponseEntity.ok(ApiResponse.success("WAC recalculated",
                Map.of("ingredientId", ingredientId, "weightedAverageCost", wac)));
    }

    // ==================== Cost History ====================

    /**
     * Get cost history for an ingredient
     */
    @GetMapping("/cost-history/{ingredientId}")
    public ResponseEntity<ApiResponse<List<IngredientCostHistory>>> getCostHistory(
            @PathVariable Long ingredientId) {
        log.info("Getting cost history for ingredient {}", ingredientId);

        List<IngredientCostHistory> history = costHistoryService.getCostHistory(ingredientId);
        return ResponseEntity.ok(ApiResponse.success("Cost history retrieved", history));
    }

    /**
     * Get paginated cost history
     */
    @GetMapping("/cost-history/{ingredientId}/paginated")
    public ResponseEntity<ApiResponse<Page<IngredientCostHistory>>> getCostHistoryPaginated(
            @PathVariable Long ingredientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Getting paginated cost history for ingredient {}", ingredientId);

        Page<IngredientCostHistory> history = costHistoryService.getCostHistoryPaginated(ingredientId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Cost history retrieved", history));
    }

    /**
     * Get cost changes in a date range
     */
    @GetMapping("/cost-history/{ingredientId}/range")
    public ResponseEntity<ApiResponse<List<IngredientCostHistory>>> getCostHistoryInRange(
            @PathVariable Long ingredientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("Getting cost history for ingredient {} from {} to {}", ingredientId, startDate, endDate);

        List<IngredientCostHistory> history = costHistoryService.getCostChangesInRange(ingredientId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Cost history retrieved", history));
    }

    /**
     * Record a manual cost change
     */
    @PostMapping("/cost-history/{ingredientId}")
    public ResponseEntity<ApiResponse<IngredientCostHistory>> recordCostChange(
            @PathVariable Long ingredientId,
            @RequestBody RecordCostChangeRequest request) {
        log.info("Recording cost change for ingredient {}: {}", ingredientId, request.newCost);

        IngredientCostHistory history = costHistoryService.recordCostChange(
                ingredientId, request.newCost, CostChangeReason.MANUAL_ADJUSTMENT, request.createdBy);

        return ResponseEntity.ok(ApiResponse.success("Cost change recorded", history));
    }

    /**
     * Get cost variance analysis
     */
    @GetMapping("/cost-variance/{ingredientId}")
    public ResponseEntity<ApiResponse<CostHistoryService.CostVariance>> getCostVariance(
            @PathVariable Long ingredientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("Getting cost variance for ingredient {} from {} to {}", ingredientId, startDate, endDate);

        CostHistoryService.CostVariance variance = costHistoryService.calculateCostVariance(
                ingredientId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success("Cost variance calculated", variance));
    }

    // ==================== Consumption History ====================

    /**
     * Get consumption history for an ingredient
     */
    @GetMapping("/consumption/{ingredientId}")
    public ResponseEntity<ApiResponse<List<BatchConsumption>>> getConsumptionHistory(
            @PathVariable Long ingredientId) {
        log.info("Getting consumption history for ingredient {}", ingredientId);

        List<BatchConsumption> history = consumptionService.getConsumptionHistory(ingredientId);
        return ResponseEntity.ok(ApiResponse.success("Consumption history retrieved", history));
    }

    /**
     * Get consumption summary by ingredient for a restaurant
     */
    @GetMapping("/consumption/summary")
    public ResponseEntity<ApiResponse<List<BatchConsumptionService.IngredientConsumptionSummary>>> getConsumptionSummary(
            @RequestParam Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("Getting consumption summary for restaurant {} from {} to {}", restaurantId, startDate, endDate);

        List<BatchConsumptionService.IngredientConsumptionSummary> summary =
                consumptionService.getConsumptionByIngredient(restaurantId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success("Consumption summary retrieved", summary));
    }

    /**
     * Get consumption statistics for an ingredient
     */
    @GetMapping("/consumption/{ingredientId}/stats")
    public ResponseEntity<ApiResponse<BatchConsumptionService.ConsumptionStats>> getConsumptionStats(
            @PathVariable Long ingredientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("Getting consumption stats for ingredient {} from {} to {}", ingredientId, startDate, endDate);

        BatchConsumptionService.ConsumptionStats stats =
                consumptionService.getConsumptionStats(ingredientId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success("Consumption stats retrieved", stats));
    }

    /**
     * Get COGS for an order
     */
    @GetMapping("/cogs/order/{orderId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderCOGS(@PathVariable Long orderId) {
        log.info("Getting COGS for order {}", orderId);

        BigDecimal cogs = consumptionService.calculateOrderCOGS(orderId);
        List<BatchConsumption> consumptions = consumptionService.getConsumptionsForOrder(orderId);

        return ResponseEntity.ok(ApiResponse.success("Order COGS calculated",
                Map.of("orderId", orderId, "cogs", cogs, "consumptions", consumptions)));
    }

    /**
     * Get total COGS for a restaurant in a date range
     */
    @GetMapping("/cogs/total")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTotalCOGS(
            @RequestParam Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("Getting total COGS for restaurant {} from {} to {}", restaurantId, startDate, endDate);

        BigDecimal cogs = consumptionService.calculateTotalCOGS(restaurantId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success("Total COGS calculated",
                Map.of("restaurantId", restaurantId, "startDate", startDate,
                        "endDate", endDate, "totalCOGS", cogs)));
    }

    // ==================== Valuation Reports ====================

    /**
     * Generate valuation comparison report (FIFO vs LIFO vs WAC)
     */
    @GetMapping("/reports/comparison")
    public ResponseEntity<ApiResponse<ValuationComparisonReport>> getValuationComparisonReport(
            @RequestParam Long restaurantId) {
        log.info("Generating valuation comparison report for restaurant {}", restaurantId);

        ValuationComparisonReport report = reportService.generateComparisonReport(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Valuation comparison report generated", report));
    }

    /**
     * Generate full inventory valuation report
     */
    @GetMapping("/reports/inventory")
    public ResponseEntity<ApiResponse<InventoryValuationReport>> getInventoryValuationReport(
            @RequestParam Long restaurantId,
            @RequestParam(required = false) ValuationMethod method) {
        log.info("Generating inventory valuation report for restaurant {} using method {}",
                restaurantId, method);

        InventoryValuationReport report = reportService.generateValuationReport(restaurantId, method);
        return ResponseEntity.ok(ApiResponse.success("Inventory valuation report generated", report));
    }

    /**
     * Generate cost variance report (Actual vs Standard)
     */
    @GetMapping("/reports/variance")
    public ResponseEntity<ApiResponse<CostVarianceReport>> getCostVarianceReport(
            @RequestParam Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("Generating cost variance report for restaurant {} from {} to {}",
                restaurantId, startDate, endDate);

        CostVarianceReport report = reportService.generateCostVarianceReport(restaurantId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Cost variance report generated", report));
    }

    // ==================== Request/Response DTOs ====================

    public record SetValuationMethodRequest(
            Long restaurantId,
            ValuationMethod valuationMethod,
            String createdBy
    ) {}

    public record RecordCostChangeRequest(
            BigDecimal newCost,
            String createdBy,
            String notes
    ) {}

    public record ValuationMethodResponse(
            Long restaurantId,
            ValuationMethod valuationMethod
    ) {}

    public record ValuationComparison(
            Long restaurantId,
            BigDecimal fifoValue,
            BigDecimal lifoValue,
            BigDecimal weightedAverageValue,
            LocalDateTime calculatedAt
    ) {}

    public record IngredientValuationResponse(
            Long ingredientId,
            ValuationMethod method,
            BigDecimal value
    ) {}
}
