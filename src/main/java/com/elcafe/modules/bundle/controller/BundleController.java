package com.elcafe.modules.bundle.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.bundle.dto.BundleRequest;
import com.elcafe.modules.bundle.dto.BundleResponse;
import com.elcafe.modules.bundle.entity.Bundle;
import com.elcafe.modules.bundle.repository.BundleRepository;
import com.elcafe.modules.bundle.service.BundleService;
import com.elcafe.utils.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BundleController {

    private final BundleService bundleService;
    private final BundleRepository bundleRepository;
    private final RestaurantAuthorizationService restaurantAuthService;

    /**
     * Get all bundles for a restaurant (admin)
     */
    @GetMapping("/restaurants/{restaurantId}/bundles")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ResponseEntity<ApiResponse<Page<BundleResponse>>> getBundles(
            @PathVariable Long restaurantId,
            @PageableDefault(size = 20, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        // Validate restaurant access to prevent cross-restaurant IDOR
        restaurantAuthService.validateRestaurantAccess(restaurantId);

        log.debug("Getting bundles for restaurant: {}", restaurantId);
        Page<BundleResponse> bundles = bundleService.getBundlesByRestaurant(restaurantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(bundles));
    }

    /**
     * Get active bundles for menu display (public)
     * @param includeAll if true, returns all active bundles regardless of time/day restrictions (for POS)
     */
    @GetMapping("/restaurants/{restaurantId}/bundles/menu")
    public ResponseEntity<ApiResponse<List<BundleResponse>>> getMenuBundles(
            @PathVariable Long restaurantId,
            @RequestParam(required = false, defaultValue = "false") boolean includeAll) {
        log.debug("Getting active bundles for restaurant menu: {}, includeAll: {}", restaurantId, includeAll);
        List<BundleResponse> bundles = bundleService.getActiveBundlesForMenu(restaurantId, includeAll);
        return ResponseEntity.ok(ApiResponse.success(bundles));
    }

    /**
     * Get a single bundle by ID
     */
    @GetMapping("/bundles/{id}")
    public ResponseEntity<ApiResponse<BundleResponse>> getBundle(@PathVariable Long id) {
        log.debug("Getting bundle: {}", id);
        BundleResponse bundle = bundleService.getBundle(id);
        return ResponseEntity.ok(ApiResponse.success(bundle));
    }

    /**
     * Create a new bundle
     */
    @PostMapping("/restaurants/{restaurantId}/bundles")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BundleResponse>> createBundle(
            @PathVariable Long restaurantId,
            @Valid @RequestBody BundleRequest request) {
        // Validate restaurant access to prevent cross-restaurant IDOR
        restaurantAuthService.validateRestaurantAccess(restaurantId);

        log.info("Creating bundle for restaurant: {}", restaurantId);
        BundleResponse created = bundleService.createBundle(restaurantId, request);
        return ResponseEntity.ok(ApiResponse.success("Bundle created successfully", created));
    }

    /**
     * Update an existing bundle
     */
    @PutMapping("/bundles/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BundleResponse>> updateBundle(
            @PathVariable Long id,
            @Valid @RequestBody BundleRequest request) {
        // Validate restaurant access to prevent cross-restaurant IDOR
        validateBundleAccess(id);

        log.info("Updating bundle: {}", id);
        BundleResponse updated = bundleService.updateBundle(id, request);
        return ResponseEntity.ok(ApiResponse.success("Bundle updated successfully", updated));
    }

    /**
     * Delete a bundle
     */
    @DeleteMapping("/bundles/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteBundle(@PathVariable Long id) {
        // Validate restaurant access to prevent cross-restaurant IDOR
        validateBundleAccess(id);

        log.info("Deleting bundle: {}", id);
        bundleService.deleteBundle(id);
        return ResponseEntity.ok(ApiResponse.success("Bundle deleted successfully", null));
    }

    /**
     * Toggle bundle active status
     */
    @PatchMapping("/bundles/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BundleResponse>> toggleBundle(@PathVariable Long id) {
        // Validate restaurant access to prevent cross-restaurant IDOR
        validateBundleAccess(id);

        log.info("Toggling bundle: {}", id);
        BundleResponse toggled = bundleService.toggleBundle(id);
        return ResponseEntity.ok(ApiResponse.success("Bundle toggled successfully", toggled));
    }

    /**
     * Validates that the current user has access to the bundle's restaurant.
     */
    private void validateBundleAccess(Long bundleId) {
        Bundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(() -> new EntityNotFoundException("Bundle not found with id: " + bundleId));
        restaurantAuthService.validateRestaurantAccess(bundle.getRestaurant().getId());
    }

    /**
     * Calculate bundle price with selected options
     */
    @PostMapping("/bundles/{id}/calculate-price")
    public ResponseEntity<ApiResponse<BigDecimal>> calculatePrice(
            @PathVariable Long id,
            @RequestBody List<Long> selectedOptionIds) {
        BigDecimal price = bundleService.calculateBundlePrice(id, selectedOptionIds);
        return ResponseEntity.ok(ApiResponse.success(price));
    }

    /**
     * Validate bundle order
     */
    @PostMapping("/bundles/{id}/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateOrder(
            @PathVariable Long id,
            @RequestBody List<Long> selectedOptionIds) {
        bundleService.validateBundleOrder(id, selectedOptionIds);
        return ResponseEntity.ok(ApiResponse.success("Bundle order is valid", true));
    }
}
