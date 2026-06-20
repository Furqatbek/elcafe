package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.financial.entity.PurchaseOrder;
import com.elcafe.modules.inventory.dto.GeneratePORequest;
import com.elcafe.modules.inventory.dto.POSuggestionResponse;
import com.elcafe.modules.inventory.service.POSuggestionService;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory/po-suggestions")
@RequiredArgsConstructor
public class POSuggestionController {

    private final POSuggestionService poSuggestionService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    /**
     * Get all PO suggestions for a restaurant, grouped by supplier
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<POSuggestionResponse>>> getSuggestions(
            @RequestParam Long restaurantId) {
        log.info("Fetching PO suggestions for restaurant: {}", restaurantId);
        restaurantAuthorizationService.checkAccess(restaurantId);

        List<POSuggestionResponse> suggestions = poSuggestionService.getSuggestions(restaurantId);

        return ResponseEntity.ok(ApiResponse.success(
                "PO suggestions retrieved successfully", suggestions));
    }

    /**
     * Get count of items needing reorder (for badge display)
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getSuggestionCount(
            @RequestParam Long restaurantId) {
        log.info("Fetching PO suggestion count for restaurant: {}", restaurantId);
        restaurantAuthorizationService.checkAccess(restaurantId);

        int count = poSuggestionService.getSuggestionCount(restaurantId);

        return ResponseEntity.ok(ApiResponse.success(
                "Count retrieved successfully", Map.of("count", count)));
    }

    /**
     * Generate a draft purchase order from a suggestion
     */
    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Long>> generatePurchaseOrder(
            @Valid @RequestBody GeneratePORequest request,
            Authentication authentication) {
        restaurantAuthorizationService.checkAccess(request.getRestaurantId());
        log.info("Generating PO for supplier: {} in restaurant: {}",
                request.getSupplierId(), request.getRestaurantId());

        String username = authentication != null ? authentication.getName() : "SYSTEM";
        PurchaseOrder po = poSuggestionService.generatePurchaseOrder(request, username);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Purchase order created successfully. PO Number: " + po.getPoNumber(),
                        po.getId()));
    }

    /**
     * Generate multiple draft purchase orders from suggestions
     */
    @PostMapping("/generate-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Long>>> generateAllPurchaseOrders(
            @RequestParam Long restaurantId,
            Authentication authentication) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        log.info("Generating all suggested POs for restaurant: {}", restaurantId);

        String username = authentication != null ? authentication.getName() : "SYSTEM";
        List<POSuggestionResponse> suggestions = poSuggestionService.getSuggestions(restaurantId);

        List<Long> createdPoIds = suggestions.stream()
                .map(suggestion -> {
                    GeneratePORequest request = GeneratePORequest.builder()
                            .restaurantId(restaurantId)
                            .supplierId(suggestion.getSupplierId())
                            .build();
                    return poSuggestionService.generatePurchaseOrder(request, username).getId();
                })
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Created " + createdPoIds.size() + " purchase orders",
                        createdPoIds));
    }
}
