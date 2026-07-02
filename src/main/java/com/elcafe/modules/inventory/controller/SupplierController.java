package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.utils.ApiResponse;
import com.elcafe.modules.inventory.dto.SupplierRequest;
import com.elcafe.modules.inventory.dto.SupplierResponse;
import com.elcafe.modules.inventory.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory/suppliers")
@RequiredArgsConstructor
@Tag(name = "Suppliers", description = "Supplier management endpoints")
@PreAuthorize("hasAnyRole('ADMIN','OWNER','MANAGER','OPERATOR')")
public class SupplierController {

    private final SupplierService supplierService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping
    @Operation(summary = "Get all suppliers for a restaurant")
    public ResponseEntity<ApiResponse<List<SupplierResponse>>> getAllByRestaurant(
            @RequestParam Long restaurantId,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<SupplierResponse> suppliers = activeOnly
                ? supplierService.getActiveByRestaurant(restaurantId)
                : supplierService.getAllByRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(suppliers));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get supplier by ID")
    public ResponseEntity<ApiResponse<SupplierResponse>> getById(@PathVariable Long id) {
        SupplierResponse supplier = supplierService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(supplier));
    }

    @PostMapping
    @Operation(summary = "Create a new supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> create(@Valid @RequestBody SupplierRequest request) {
        restaurantAuthorizationService.checkAccess(request.getRestaurantId());
        SupplierResponse supplier = supplierService.create(request);
        return ResponseEntity.ok(ApiResponse.success(supplier));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request) {
        SupplierResponse supplier = supplierService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(supplier));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a supplier (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/toggle")
    @Operation(summary = "Toggle supplier active status")
    public ResponseEntity<ApiResponse<SupplierResponse>> toggleActive(@PathVariable Long id) {
        SupplierResponse supplier = supplierService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success(supplier));
    }
}
