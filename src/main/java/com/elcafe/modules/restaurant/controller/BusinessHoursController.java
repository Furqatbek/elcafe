package com.elcafe.modules.restaurant.controller;

import com.elcafe.modules.restaurant.dto.BusinessHoursResponse;
import com.elcafe.modules.restaurant.dto.CreateBusinessHoursRequest;
import com.elcafe.modules.restaurant.dto.UpdateBusinessHoursRequest;
import com.elcafe.modules.restaurant.service.BusinessHoursService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/business-hours")
@RequiredArgsConstructor
@Tag(name = "Business Hours", description = "Business hours management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class BusinessHoursController {

    private final BusinessHoursService businessHoursService;

    @GetMapping
    @Operation(summary = "List business hours", description = "Get all business hours for a restaurant")
    public ResponseEntity<ApiResponse<List<BusinessHoursResponse>>> getAllBusinessHours(
            @PathVariable Long restaurantId
    ) {
        List<BusinessHoursResponse> response = businessHoursService.getAllByRestaurantId(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get business hours", description = "Get business hours by ID")
    public ResponseEntity<ApiResponse<BusinessHoursResponse>> getBusinessHours(
            @PathVariable Long restaurantId,
            @PathVariable Long id
    ) {
        BusinessHoursResponse response = businessHoursService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create business hours", description = "Create new business hours for a restaurant (Admin only)")
    public ResponseEntity<ApiResponse<BusinessHoursResponse>> createBusinessHours(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateBusinessHoursRequest request
    ) {
        // Ensure the restaurantId in the path matches the request
        if (!restaurantId.equals(request.getRestaurantId())) {
            throw new IllegalArgumentException("Restaurant ID in path does not match request body");
        }

        BusinessHoursResponse response = businessHoursService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Business hours created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update business hours", description = "Update business hours details (Admin only)")
    public ResponseEntity<ApiResponse<BusinessHoursResponse>> updateBusinessHours(
            @PathVariable Long restaurantId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateBusinessHoursRequest request
    ) {
        BusinessHoursResponse response = businessHoursService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Business hours updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete business hours", description = "Delete business hours (Admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteBusinessHours(
            @PathVariable Long restaurantId,
            @PathVariable Long id
    ) {
        businessHoursService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Business hours deleted successfully", null));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete all business hours", description = "Delete all business hours for a restaurant (Admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteAllBusinessHours(
            @PathVariable Long restaurantId
    ) {
        businessHoursService.deleteAllByRestaurantId(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("All business hours deleted successfully", null));
    }
}
