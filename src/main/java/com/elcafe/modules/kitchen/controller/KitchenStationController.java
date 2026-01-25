package com.elcafe.modules.kitchen.controller;

import com.elcafe.modules.kitchen.dto.CreateKitchenStationRequest;
import com.elcafe.modules.kitchen.dto.KitchenStationDTO;
import com.elcafe.modules.kitchen.dto.UpdateKitchenStationRequest;
import com.elcafe.modules.kitchen.service.KitchenStationService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/kitchen/stations")
@RequiredArgsConstructor
@Tag(name = "Kitchen Stations", description = "Kitchen station management for ticket routing")
public class KitchenStationController {

    private final KitchenStationService kitchenStationService;

    @GetMapping
    @Operation(summary = "Get all kitchen stations", description = "Get all kitchen stations for a restaurant")
    public ResponseEntity<ApiResponse<List<KitchenStationDTO>>> getStations(
            @RequestParam Long restaurantId) {
        log.info("Fetching kitchen stations for restaurant: {}", restaurantId);
        List<KitchenStationDTO> stations = kitchenStationService.getStationsByRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Kitchen stations retrieved successfully", stations));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active kitchen stations", description = "Get only active kitchen stations for a restaurant")
    public ResponseEntity<ApiResponse<List<KitchenStationDTO>>> getActiveStations(
            @RequestParam Long restaurantId) {
        log.info("Fetching active kitchen stations for restaurant: {}", restaurantId);
        List<KitchenStationDTO> stations = kitchenStationService.getActiveStationsByRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Active kitchen stations retrieved successfully", stations));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get kitchen station by ID", description = "Get a single kitchen station by its ID")
    public ResponseEntity<ApiResponse<KitchenStationDTO>> getStation(@PathVariable Long id) {
        log.info("Fetching kitchen station: {}", id);
        KitchenStationDTO station = kitchenStationService.getStationById(id);
        return ResponseEntity.ok(ApiResponse.success("Kitchen station retrieved successfully", station));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create kitchen station", description = "Create a new kitchen station")
    public ResponseEntity<ApiResponse<KitchenStationDTO>> createStation(
            @Valid @RequestBody CreateKitchenStationRequest request) {
        log.info("Creating kitchen station: {}", request.getName());
        KitchenStationDTO station = kitchenStationService.createStation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Kitchen station created successfully", station));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update kitchen station", description = "Update an existing kitchen station")
    public ResponseEntity<ApiResponse<KitchenStationDTO>> updateStation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateKitchenStationRequest request) {
        log.info("Updating kitchen station: {}", id);
        KitchenStationDTO station = kitchenStationService.updateStation(id, request);
        return ResponseEntity.ok(ApiResponse.success("Kitchen station updated successfully", station));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete kitchen station", description = "Delete a kitchen station")
    public ResponseEntity<ApiResponse<Void>> deleteStation(@PathVariable Long id) {
        log.info("Deleting kitchen station: {}", id);
        kitchenStationService.deleteStation(id);
        return ResponseEntity.ok(ApiResponse.success("Kitchen station deleted successfully", null));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Toggle kitchen station", description = "Toggle kitchen station active status")
    public ResponseEntity<ApiResponse<KitchenStationDTO>> toggleStation(@PathVariable Long id) {
        log.info("Toggling kitchen station: {}", id);
        KitchenStationDTO station = kitchenStationService.toggleStation(id);
        return ResponseEntity.ok(ApiResponse.success("Kitchen station toggled successfully", station));
    }
}
