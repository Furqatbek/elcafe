package com.elcafe.modules.inventory.controller;

import com.elcafe.modules.inventory.dto.*;
import com.elcafe.modules.inventory.entity.ProductionBatch;
import com.elcafe.modules.inventory.entity.ProductionBatchInput;
import com.elcafe.modules.inventory.service.ProductionBatchService;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory/production-batches")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'KITCHEN_STAFF')")
public class ProductionBatchController {

    private final ProductionBatchService productionBatchService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductionBatchResponse>> createBatch(
            @Valid @RequestBody CreateProductionBatchRequest request) {
        log.info("Creating production batch '{}' for restaurant {}", request.getName(), request.getRestaurantId());

        ProductionBatch batch = productionBatchService.createBatch(request);
        ProductionBatchResponse response = ProductionBatchResponse.fromEntity(batch);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Production batch created successfully", response));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<List<ProductionBatchSummary>>> getBatches(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) ProductionBatch.Status status) {
        log.info("Fetching production batches for restaurant {}, status={}", restaurantId, status);

        List<ProductionBatch> batches;
        if (status != null) {
            batches = productionBatchService.getBatchesByStatus(restaurantId, status);
        } else {
            batches = productionBatchService.getBatchesByRestaurant(restaurantId);
        }

        List<ProductionBatchSummary> summaries = batches.stream()
                .map(ProductionBatchSummary::fromEntity)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Production batches retrieved successfully", summaries));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductionBatchResponse>> getBatch(@PathVariable Long id) {
        log.info("Fetching production batch {}", id);

        ProductionBatch batch = productionBatchService.getBatchById(id);
        ProductionBatchResponse response = ProductionBatchResponse.fromEntity(batch);

        return ResponseEntity.ok(ApiResponse.success("Production batch retrieved successfully", response));
    }

    @PostMapping("/{id}/inputs")
    public ResponseEntity<ApiResponse<ProductionBatchResponse.InputDetail>> addInput(
            @PathVariable Long id,
            @Valid @RequestBody AddInputRequest request) {
        log.info("Adding input to production batch {}: ingredient {}", id, request.getIngredientId());

        ProductionBatchInput input = productionBatchService.addInput(id, request);

        ProductionBatchResponse.InputDetail detail = ProductionBatchResponse.InputDetail.builder()
                .id(input.getId())
                .ingredientId(input.getIngredient().getId())
                .ingredientName(input.getIngredient().getName())
                .plannedQuantity(input.getPlannedQuantity())
                .actualQuantity(input.getActualQuantity())
                .unit(input.getUnit())
                .costPerUnit(input.getCostPerUnit())
                .totalCost(input.getTotalCost())
                .notes(input.getNotes())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Input added successfully", detail));
    }

    @PutMapping("/{id}/inputs/{inputId}")
    public ResponseEntity<ApiResponse<ProductionBatchResponse.InputDetail>> updateInput(
            @PathVariable Long id,
            @PathVariable Long inputId,
            @Valid @RequestBody AddInputRequest request) {
        log.info("Updating input {} for production batch {}", inputId, id);

        ProductionBatchInput input = productionBatchService.updateInput(id, inputId, request);

        ProductionBatchResponse.InputDetail detail = ProductionBatchResponse.InputDetail.builder()
                .id(input.getId())
                .ingredientId(input.getIngredient().getId())
                .ingredientName(input.getIngredient().getName())
                .plannedQuantity(input.getPlannedQuantity())
                .actualQuantity(input.getActualQuantity())
                .unit(input.getUnit())
                .costPerUnit(input.getCostPerUnit())
                .totalCost(input.getTotalCost())
                .notes(input.getNotes())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Input updated successfully", detail));
    }

    @PostMapping("/{id}/reload-recipe")
    public ResponseEntity<ApiResponse<ProductionBatchResponse>> reloadRecipe(@PathVariable Long id) {
        log.info("Reloading recipe inputs for production batch {}", id);

        ProductionBatch batch = productionBatchService.reloadRecipeInputs(id);
        ProductionBatchResponse response = ProductionBatchResponse.fromEntity(batch);

        return ResponseEntity.ok(ApiResponse.success("Recipe inputs reloaded successfully", response));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<ProductionBatchResponse>> startBatch(@PathVariable Long id) {
        log.info("Starting production batch {}", id);

        ProductionBatch batch = productionBatchService.startBatch(id);
        ProductionBatchResponse response = ProductionBatchResponse.fromEntity(batch);

        return ResponseEntity.ok(ApiResponse.success("Production batch started", response));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ProductionBatchResponse>> completeBatch(
            @PathVariable Long id,
            @Valid @RequestBody CompleteBatchRequest request) {
        log.info("Completing production batch {} with output {} {}", id, request.getOutputQuantity(), request.getOutputUnit());

        ProductionBatch batch = productionBatchService.completeBatch(id, request);
        ProductionBatchResponse response = ProductionBatchResponse.fromEntity(batch);

        return ResponseEntity.ok(ApiResponse.success("Production batch completed", response));
    }

    @PostMapping("/{id}/waste")
    public ResponseEntity<ApiResponse<Void>> recordWaste(
            @PathVariable Long id,
            @RequestBody WasteRequest request) {
        log.info("Recording waste for production batch {}: {} (reason: {})",
                id, request.quantity, request.reason);

        productionBatchService.recordWaste(id, request.quantity, request.reason);

        return ResponseEntity.ok(ApiResponse.success("Waste recorded successfully", null));
    }

    @GetMapping("/restaurant/{restaurantId}/available")
    public ResponseEntity<ApiResponse<List<ProductionBatchSummary>>> getAvailableBatches(
            @PathVariable Long restaurantId) {
        log.info("Fetching available production batches for restaurant {}", restaurantId);

        List<ProductionBatch> batches = productionBatchService.getActiveBatches(restaurantId);
        List<ProductionBatchSummary> summaries = batches.stream()
                .map(ProductionBatchSummary::fromEntity)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Available batches retrieved successfully", summaries));
    }

    @GetMapping("/restaurant/{restaurantId}/report")
    public ResponseEntity<ApiResponse<List<Object[]>>> getCostReport(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("Generating production cost report for restaurant {} from {} to {}", restaurantId, from, to);

        List<Object[]> report = productionBatchService.getBatchCostReport(restaurantId, from, to);

        return ResponseEntity.ok(ApiResponse.success("Cost report generated successfully", report));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBatch(@PathVariable Long id) {
        log.info("Deleting production batch {}", id);

        productionBatchService.deleteBatch(id);

        return ResponseEntity.ok(ApiResponse.success("Production batch deleted successfully", null));
    }

    /**
     * Simple request body for waste recording
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WasteRequest {
        private BigDecimal quantity;
        private String reason;
    }
}
