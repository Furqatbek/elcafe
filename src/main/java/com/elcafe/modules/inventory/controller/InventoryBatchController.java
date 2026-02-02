package com.elcafe.modules.inventory.controller;

import com.elcafe.modules.inventory.dto.BatchRequest;
import com.elcafe.modules.inventory.dto.BatchResponse;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.service.InventoryBatchService;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory/batches")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'OPERATOR')")
public class InventoryBatchController {

    private final InventoryBatchService batchService;

    /**
     * Create a new batch
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BatchResponse>> createBatch(
            @Valid @RequestBody BatchRequest request) {
        log.info("Creating batch for ingredient: {}", request.getIngredientId());

        InventoryBatch batch = batchService.createBatch(request);
        int alertDays = batch.getIngredient().getExpiryAlertDays() != null ?
                batch.getIngredient().getExpiryAlertDays() : 7;

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Batch created successfully", BatchResponse.fromEntity(batch, alertDays)));
    }

    /**
     * Get all batches for an ingredient
     */
    @GetMapping("/ingredient/{ingredientId}")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getBatchesByIngredient(
            @PathVariable Long ingredientId) {
        log.info("Getting batches for ingredient: {}", ingredientId);

        List<BatchResponse> batches = batchService.getBatchesByIngredient(ingredientId);
        return ResponseEntity.ok(ApiResponse.success("Batches retrieved successfully", batches));
    }

    /**
     * Get expiring batches for a restaurant
     */
    @GetMapping("/expiring")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getExpiringBatches(
            @RequestParam Long restaurantId,
            @RequestParam(defaultValue = "7") int withinDays) {
        log.info("Getting batches expiring within {} days for restaurant: {}", withinDays, restaurantId);

        List<BatchResponse> batches = batchService.getExpiringBatches(restaurantId, withinDays);
        return ResponseEntity.ok(ApiResponse.success("Expiring batches retrieved successfully", batches));
    }

    /**
     * Get expired batches for a restaurant
     */
    @GetMapping("/expired")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getExpiredBatches(
            @RequestParam Long restaurantId) {
        log.info("Getting expired batches for restaurant: {}", restaurantId);

        List<BatchResponse> batches = batchService.getExpiredBatches(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Expired batches retrieved successfully", batches));
    }

    /**
     * Get expiry summary for a restaurant
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<InventoryBatchService.ExpirySummary>> getExpirySummary(
            @RequestParam Long restaurantId,
            @RequestParam(defaultValue = "7") int alertDays) {
        log.info("Getting expiry summary for restaurant: {}", restaurantId);

        InventoryBatchService.ExpirySummary summary = batchService.getExpirySummary(restaurantId, alertDays);
        return ResponseEntity.ok(ApiResponse.success("Expiry summary retrieved successfully", summary));
    }

    /**
     * Update batch expiry date
     */
    @PatchMapping("/{batchId}/expiry")
    public ResponseEntity<ApiResponse<BatchResponse>> updateBatchExpiry(
            @PathVariable Long batchId,
            @RequestBody Map<String, String> request) {
        log.info("Updating expiry date for batch: {}", batchId);

        LocalDate newExpiryDate = LocalDate.parse(request.get("expiryDate"));
        InventoryBatch batch = batchService.updateBatchExpiry(batchId, newExpiryDate);
        int alertDays = batch.getIngredient().getExpiryAlertDays() != null ?
                batch.getIngredient().getExpiryAlertDays() : 7;

        return ResponseEntity.ok(ApiResponse.success("Batch expiry updated successfully",
                BatchResponse.fromEntity(batch, alertDays)));
    }

    /**
     * Write off a batch
     */
    @PostMapping("/{batchId}/write-off")
    public ResponseEntity<ApiResponse<Void>> writeOffBatch(
            @PathVariable Long batchId,
            @RequestBody Map<String, String> request) {
        log.info("Writing off batch: {}", batchId);

        String reason = request.getOrDefault("reason", "Manual write-off");
        batchService.writeOffBatch(batchId, reason);

        return ResponseEntity.ok(ApiResponse.success("Batch written off successfully", null));
    }

    /**
     * Mark expired batches as expired (admin endpoint for scheduled task)
     */
    @PostMapping("/mark-expired")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markExpiredBatches(
            @RequestParam Long restaurantId) {
        log.info("Marking expired batches for restaurant: {}", restaurantId);

        int count = batchService.markExpiredBatches(restaurantId);

        return ResponseEntity.ok(ApiResponse.success(
                count + " batches marked as expired",
                Map.of("count", count)));
    }
}
