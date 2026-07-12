package com.elcafe.modules.inventory.controller;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.inventory.dto.BatchRequest;
import com.elcafe.modules.inventory.dto.BatchResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.service.InventoryBatchService;
import com.elcafe.security.UserPrincipal;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final InventoryBatchRepository batchRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    /**
     * Create a new batch
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BatchResponse>> createBatch(
            @Valid @RequestBody BatchRequest request) {
        // Validate restaurant access via ingredient - prevents IDOR
        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));
        restaurantAuthorizationService.validateRestaurantAccess(ingredient.getRestaurant().getId());

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
        // Validate restaurant access via ingredient - prevents IDOR
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));
        restaurantAuthorizationService.validateRestaurantAccess(ingredient.getRestaurant().getId());

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
        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(restaurantId);
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
        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(restaurantId);
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
        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(restaurantId);
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
        // Validate restaurant access via batch's ingredient - prevents IDOR
        InventoryBatch existingBatch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found"));
        restaurantAuthorizationService.validateRestaurantAccess(
                existingBatch.getIngredient().getRestaurant().getId());

        log.info("Updating expiry date for batch: {}", batchId);

        LocalDate newExpiryDate = LocalDate.parse(request.get("expiryDate"));
        InventoryBatch batch = batchService.updateBatchExpiry(batchId, newExpiryDate);
        int alertDays = batch.getIngredient().getExpiryAlertDays() != null ?
                batch.getIngredient().getExpiryAlertDays() : 7;

        return ResponseEntity.ok(ApiResponse.success("Batch expiry updated successfully",
                BatchResponse.fromEntity(batch, alertDays)));
    }

    /**
     * Write off a batch with proper audit trail using authenticated user.
     * This is a sensitive operation that reduces inventory, so it requires MANAGER or above role.
     * Prevents falsified audit trails by using authenticated user's identity.
     */
    @PostMapping("/{batchId}/write-off")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> writeOffBatch(
            @PathVariable Long batchId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        // Validate restaurant access via batch's ingredient - prevents IDOR
        InventoryBatch existingBatch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found"));
        restaurantAuthorizationService.validateRestaurantAccess(
                existingBatch.getIngredient().getRestaurant().getId());

        log.info("Writing off batch: {} by user: {} (role: {})",
                batchId, currentUser.getEmail(), currentUser.getRole());

        String reason = request.getOrDefault("reason", "Manual write-off");
        // Use authenticated user's identity for audit trail - prevents falsification
        String recordedBy = String.format("%s (ID:%d)", currentUser.getEmail(), currentUser.getId());
        batchService.writeOffBatch(batchId, reason, recordedBy);

        return ResponseEntity.ok(ApiResponse.success("Batch written off successfully", null));
    }

    /**
     * Mark expired batches as expired (admin endpoint for scheduled task)
     */
    @PostMapping("/mark-expired")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markExpiredBatches(
            @RequestParam Long restaurantId) {
        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(restaurantId);
        log.info("Marking expired batches for restaurant: {}", restaurantId);

        int count = batchService.markExpiredBatches(restaurantId);

        return ResponseEntity.ok(ApiResponse.success(
                count + " batches marked as expired",
                Map.of("count", count)));
    }
}
