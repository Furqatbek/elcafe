package com.elcafe.modules.courier.controller;

import com.elcafe.modules.courier.dto.CourierDTO;
import com.elcafe.modules.courier.dto.CourierWalletDTO;
import com.elcafe.modules.courier.dto.CreateCourierRequest;
import com.elcafe.modules.courier.dto.UpdateCourierRequest;
import com.elcafe.modules.courier.service.CourierService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/couriers")
@RequiredArgsConstructor
@Tag(name = "Couriers", description = "Courier management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class CourierController {

    private final CourierService courierService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get all couriers", description = "Get paginated list of all couriers")
    public ResponseEntity<ApiResponse<Page<CourierDTO>>> getAllCouriers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<CourierDTO> couriers = courierService.getAllCouriers(pageable);

        return ResponseEntity.ok(ApiResponse.success("Couriers retrieved successfully", couriers));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get courier by ID", description = "Get courier details by ID")
    public ResponseEntity<ApiResponse<CourierDTO>> getCourierById(@PathVariable Long id) {
        CourierDTO courier = courierService.getCourierById(id);
        return ResponseEntity.ok(ApiResponse.success("Courier retrieved successfully", courier));
    }

    @GetMapping("/{id}/wallet")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'COURIER')")
    @Operation(summary = "Get courier wallet", description = "Get courier wallet balance and statistics")
    public ResponseEntity<ApiResponse<CourierWalletDTO>> getCourierWallet(@PathVariable Long id) {
        CourierWalletDTO wallet = courierService.getCourierWallet(id);
        return ResponseEntity.ok(ApiResponse.success("Wallet retrieved successfully", wallet));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create courier", description = "Create a new courier with wallet")
    public ResponseEntity<ApiResponse<CourierDTO>> createCourier(
            @Valid @RequestBody CreateCourierRequest request) {
        CourierDTO courier = courierService.createCourier(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Courier created successfully", courier));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update courier", description = "Update existing courier")
    public ResponseEntity<ApiResponse<CourierDTO>> updateCourier(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourierRequest request) {
        CourierDTO courier = courierService.updateCourier(id, request);
        return ResponseEntity.ok(ApiResponse.success("Courier updated successfully", courier));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete courier", description = "Delete courier by ID")
    public ResponseEntity<ApiResponse<Void>> deleteCourier(@PathVariable Long id) {
        courierService.deleteCourier(id);
        return ResponseEntity.ok(ApiResponse.success("Courier deleted successfully", null));
    }
}
