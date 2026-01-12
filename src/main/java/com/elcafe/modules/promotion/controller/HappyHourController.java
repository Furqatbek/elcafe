package com.elcafe.modules.promotion.controller;

import com.elcafe.common.dto.ApiResponse;
import com.elcafe.modules.promotion.dto.ActiveHappyHourResponse;
import com.elcafe.modules.promotion.dto.HappyHourRequest;
import com.elcafe.modules.promotion.dto.HappyHourResponse;
import com.elcafe.modules.promotion.service.HappyHourService;
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

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HappyHourController {

    private final HappyHourService happyHourService;

    /**
     * Get all happy hours for a restaurant
     */
    @GetMapping("/restaurants/{restaurantId}/happy-hours")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ResponseEntity<ApiResponse<Page<HappyHourResponse>>> getHappyHours(
            @PathVariable Long restaurantId,
            @PageableDefault(size = 20, sort = "priority", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Getting happy hours for restaurant: {}", restaurantId);
        Page<HappyHourResponse> happyHours = happyHourService.getHappyHoursByRestaurant(restaurantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(happyHours));
    }

    /**
     * Get a single happy hour by ID
     */
    @GetMapping("/happy-hours/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ResponseEntity<ApiResponse<HappyHourResponse>> getHappyHour(@PathVariable Long id) {
        log.debug("Getting happy hour: {}", id);
        HappyHourResponse happyHour = happyHourService.getHappyHour(id);
        return ResponseEntity.ok(ApiResponse.success(happyHour));
    }

    /**
     * Create a new happy hour
     */
    @PostMapping("/restaurants/{restaurantId}/happy-hours")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<HappyHourResponse>> createHappyHour(
            @PathVariable Long restaurantId,
            @Valid @RequestBody HappyHourRequest request) {
        log.info("Creating happy hour for restaurant: {}", restaurantId);
        HappyHourResponse created = happyHourService.createHappyHour(restaurantId, request);
        return ResponseEntity.ok(ApiResponse.success(created, "Happy hour created successfully"));
    }

    /**
     * Update an existing happy hour
     */
    @PutMapping("/happy-hours/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<HappyHourResponse>> updateHappyHour(
            @PathVariable Long id,
            @Valid @RequestBody HappyHourRequest request) {
        log.info("Updating happy hour: {}", id);
        HappyHourResponse updated = happyHourService.updateHappyHour(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Happy hour updated successfully"));
    }

    /**
     * Delete a happy hour
     */
    @DeleteMapping("/happy-hours/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteHappyHour(@PathVariable Long id) {
        log.info("Deleting happy hour: {}", id);
        happyHourService.deleteHappyHour(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Happy hour deleted successfully"));
    }

    /**
     * Toggle happy hour active status
     */
    @PatchMapping("/happy-hours/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<HappyHourResponse>> toggleHappyHour(@PathVariable Long id) {
        log.info("Toggling happy hour: {}", id);
        HappyHourResponse toggled = happyHourService.toggleHappyHour(id);
        return ResponseEntity.ok(ApiResponse.success(toggled, "Happy hour toggled successfully"));
    }

    /**
     * Check if happy hour is currently active for a restaurant
     */
    @GetMapping("/restaurants/{restaurantId}/happy-hours/active")
    public ResponseEntity<ApiResponse<ActiveHappyHourResponse>> getActiveHappyHour(
            @PathVariable Long restaurantId) {
        log.debug("Checking active happy hour for restaurant: {}", restaurantId);
        Optional<ActiveHappyHourResponse> active = happyHourService.getActiveHappyHour(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(active.orElse(null)));
    }

    /**
     * Check if any happy hour is active (returns boolean)
     */
    @GetMapping("/restaurants/{restaurantId}/happy-hours/is-active")
    public ResponseEntity<ApiResponse<Boolean>> isHappyHourActive(@PathVariable Long restaurantId) {
        boolean isActive = happyHourService.isHappyHourActive(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(isActive));
    }
}
