package com.elcafe.modules.restaurant.controller;

import com.elcafe.modules.restaurant.dto.RestaurantRequest;
import com.elcafe.modules.restaurant.dto.RestaurantResponse;
import com.elcafe.modules.restaurant.service.RestaurantService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
@Tag(name = "Restaurant", description = "Restaurant management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class RestaurantController {

    private final RestaurantService restaurantService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create restaurant", description = "Create a new restaurant (Admin only)")
    public ResponseEntity<ApiResponse<RestaurantResponse>> createRestaurant(
            @Valid @RequestBody RestaurantRequest request
    ) {
        RestaurantResponse response = restaurantService.createRestaurant(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Restaurant created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update restaurant", description = "Update restaurant details (Admin only)")
    public ResponseEntity<ApiResponse<RestaurantResponse>> updateRestaurant(
            @PathVariable Long id,
            @Valid @RequestBody RestaurantRequest request
    ) {
        RestaurantResponse response = restaurantService.updateRestaurant(id, request);
        return ResponseEntity.ok(ApiResponse.success("Restaurant updated successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get restaurant", description = "Get restaurant by ID")
    public ResponseEntity<ApiResponse<RestaurantResponse>> getRestaurant(@PathVariable Long id) {
        RestaurantResponse response = restaurantService.getRestaurantById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "List restaurants", description = "Get all restaurants with pagination")
    public ResponseEntity<ApiResponse<Page<RestaurantResponse>>> getAllRestaurants(Pageable pageable) {
        Page<RestaurantResponse> response = restaurantService.getAllRestaurants(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/active")
    @Operation(summary = "List active restaurants", description = "Get all active restaurants")
    public ResponseEntity<ApiResponse<List<RestaurantResponse>>> getActiveRestaurants() {
        List<RestaurantResponse> response = restaurantService.getActiveRestaurants();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/accepting-orders")
    @Operation(summary = "List accepting orders", description = "Get restaurants currently accepting orders")
    public ResponseEntity<ApiResponse<List<RestaurantResponse>>> getAcceptingOrdersRestaurants() {
        List<RestaurantResponse> response = restaurantService.getAcceptingOrdersRestaurants();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete restaurant", description = "Delete restaurant (Admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteRestaurant(@PathVariable Long id) {
        restaurantService.deleteRestaurant(id);
        return ResponseEntity.ok(ApiResponse.success("Restaurant deleted successfully", null));
    }
}
