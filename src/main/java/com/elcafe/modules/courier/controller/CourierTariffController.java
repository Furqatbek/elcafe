package com.elcafe.modules.courier.controller;

import com.elcafe.modules.courier.dto.CourierTariffResponse;
import com.elcafe.modules.courier.dto.CreateCourierTariffRequest;
import com.elcafe.modules.courier.dto.UpdateCourierTariffRequest;
import com.elcafe.modules.courier.enums.TariffType;
import com.elcafe.modules.courier.service.CourierTariffService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/couriers/tariffs")
@RequiredArgsConstructor
@Tag(name = "Courier Tariffs", description = "Courier tariff management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class CourierTariffController {

    private final CourierTariffService courierTariffService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get all tariffs", description = "Get paginated list of all courier tariffs")
    public ResponseEntity<ApiResponse<Page<CourierTariffResponse>>> getAllTariffs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) TariffType type) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<CourierTariffResponse> tariffs = type != null
                ? courierTariffService.getTariffsByType(type, pageable)
                : courierTariffService.getAllTariffs(pageable);

        return ResponseEntity.ok(ApiResponse.success("Tariffs retrieved successfully", tariffs));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get active tariffs", description = "Get all active courier tariffs")
    public ResponseEntity<ApiResponse<List<CourierTariffResponse>>> getActiveTariffs(
            @RequestParam(required = false) TariffType type) {

        List<CourierTariffResponse> tariffs = type != null
                ? courierTariffService.getActiveTariffsByType(type)
                : courierTariffService.getActiveTariffs();

        return ResponseEntity.ok(ApiResponse.success("Active tariffs retrieved successfully", tariffs));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get tariff by ID", description = "Get courier tariff details by ID")
    public ResponseEntity<ApiResponse<CourierTariffResponse>> getTariffById(@PathVariable Long id) {
        CourierTariffResponse tariff = courierTariffService.getTariffById(id);
        return ResponseEntity.ok(ApiResponse.success("Tariff retrieved successfully", tariff));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create tariff", description = "Create a new courier tariff")
    public ResponseEntity<ApiResponse<CourierTariffResponse>> createTariff(
            @Valid @RequestBody CreateCourierTariffRequest request) {
        CourierTariffResponse tariff = courierTariffService.createTariff(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tariff created successfully", tariff));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update tariff", description = "Update existing courier tariff")
    public ResponseEntity<ApiResponse<CourierTariffResponse>> updateTariff(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourierTariffRequest request) {
        CourierTariffResponse tariff = courierTariffService.updateTariff(id, request);
        return ResponseEntity.ok(ApiResponse.success("Tariff updated successfully", tariff));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete tariff", description = "Delete courier tariff by ID")
    public ResponseEntity<ApiResponse<Void>> deleteTariff(@PathVariable Long id) {
        courierTariffService.deleteTariff(id);
        return ResponseEntity.ok(ApiResponse.success("Tariff deleted successfully", null));
    }
}
