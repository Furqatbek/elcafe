package com.elcafe.modules.restaurant.controller;

import com.elcafe.modules.restaurant.dto.CreateDeliveryZoneRequest;
import com.elcafe.modules.restaurant.dto.DeliveryZoneResponse;
import com.elcafe.modules.restaurant.dto.UpdateDeliveryZoneRequest;
import com.elcafe.modules.restaurant.service.DeliveryZoneService;
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
@RequestMapping("/api/v1/restaurants/{restaurantId}/delivery-zones")
@RequiredArgsConstructor
@Tag(name = "Delivery Zones", description = "Delivery zone management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class DeliveryZoneController {

    private final DeliveryZoneService deliveryZoneService;

    @GetMapping
    @Operation(summary = "List delivery zones", description = "Get all delivery zones for a restaurant")
    public ResponseEntity<ApiResponse<List<DeliveryZoneResponse>>> getAllDeliveryZones(
            @PathVariable Long restaurantId
    ) {
        List<DeliveryZoneResponse> response = deliveryZoneService.getAllByRestaurantId(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get delivery zone", description = "Get delivery zone by ID")
    public ResponseEntity<ApiResponse<DeliveryZoneResponse>> getDeliveryZone(
            @PathVariable Long restaurantId,
            @PathVariable Long id
    ) {
        DeliveryZoneResponse response = deliveryZoneService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create delivery zone", description = "Create new delivery zone for a restaurant (Admin only)")
    public ResponseEntity<ApiResponse<DeliveryZoneResponse>> createDeliveryZone(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateDeliveryZoneRequest request
    ) {
        // Ensure the restaurantId in the path matches the request
        if (!restaurantId.equals(request.getRestaurantId())) {
            throw new IllegalArgumentException("Restaurant ID in path does not match request body");
        }

        DeliveryZoneResponse response = deliveryZoneService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Delivery zone created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update delivery zone", description = "Update delivery zone details (Admin only)")
    public ResponseEntity<ApiResponse<DeliveryZoneResponse>> updateDeliveryZone(
            @PathVariable Long restaurantId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateDeliveryZoneRequest request
    ) {
        DeliveryZoneResponse response = deliveryZoneService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Delivery zone updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete delivery zone", description = "Delete delivery zone (Admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteDeliveryZone(
            @PathVariable Long restaurantId,
            @PathVariable Long id
    ) {
        deliveryZoneService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Delivery zone deleted successfully", null));
    }
}
