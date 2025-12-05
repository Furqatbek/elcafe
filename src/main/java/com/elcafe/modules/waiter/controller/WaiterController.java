package com.elcafe.modules.waiter.controller;

import com.elcafe.modules.waiter.dto.CreateWaiterRequest;
import com.elcafe.modules.waiter.dto.UpdateWaiterRequest;
import com.elcafe.modules.waiter.dto.WaiterAuthRequest;
import com.elcafe.modules.waiter.dto.WaiterAuthResponse;
import com.elcafe.modules.waiter.dto.WaiterResponse;
import com.elcafe.modules.waiter.entity.Table;
import com.elcafe.modules.waiter.service.WaiterService;
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

/**
 * Controller for waiter management and authentication
 */
@RestController
@RequestMapping("/api/v1/waiters")
@RequiredArgsConstructor
@Tag(name = "Waiters", description = "Waiter management and authentication endpoints")
@SecurityRequirement(name = "bearerAuth")
public class WaiterController {

    private final WaiterService waiterService;

    @PostMapping("/auth")
    @Operation(summary = "Authenticate waiter", description = "Authenticate waiter using PIN code")
    public ResponseEntity<ApiResponse<WaiterAuthResponse>> authenticate(
            @Valid @RequestBody WaiterAuthRequest request) {
        WaiterAuthResponse response = waiterService.authenticate(request);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SUPERVISOR')")
    @Operation(summary = "Get all waiters", description = "Get paginated list of all waiters")
    public ResponseEntity<ApiResponse<Page<WaiterResponse>>> getAllWaiters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<WaiterResponse> waiters = waiterService.getAllWaiters(pageable);

        return ResponseEntity.ok(ApiResponse.success("Waiters retrieved successfully", waiters));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SUPERVISOR')")
    @Operation(summary = "Get active waiters", description = "Get list of all active waiters")
    public ResponseEntity<ApiResponse<List<WaiterResponse>>> getActiveWaiters() {
        List<WaiterResponse> waiters = waiterService.getActiveWaiters();
        return ResponseEntity.ok(ApiResponse.success("Active waiters retrieved successfully", waiters));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SUPERVISOR', 'WAITER')")
    @Operation(summary = "Get waiter by ID", description = "Get waiter details by ID")
    public ResponseEntity<ApiResponse<WaiterResponse>> getWaiterById(@PathVariable Long id) {
        WaiterResponse waiter = waiterService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Waiter retrieved successfully", waiter));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('WAITER')")
    @Operation(summary = "Get current waiter profile", description = "Get authenticated waiter's profile")
    public ResponseEntity<ApiResponse<WaiterResponse>> getMyProfile(
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        WaiterResponse waiter = waiterService.getById(waiterId);
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", waiter));
    }

    @GetMapping("/me/tables")
    @PreAuthorize("hasRole('WAITER')")
    @Operation(summary = "Get assigned tables", description = "Get tables assigned to authenticated waiter")
    public ResponseEntity<ApiResponse<List<Table>>> getMyTables(
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        List<Table> tables = waiterService.getActiveTables(waiterId);
        return ResponseEntity.ok(ApiResponse.success("Tables retrieved successfully", tables));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Create waiter", description = "Create a new waiter")
    public ResponseEntity<ApiResponse<WaiterResponse>> createWaiter(
            @Valid @RequestBody CreateWaiterRequest request) {
        WaiterResponse waiter = waiterService.createWaiter(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Waiter created successfully", waiter));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Update waiter", description = "Update existing waiter")
    public ResponseEntity<ApiResponse<WaiterResponse>> updateWaiter(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWaiterRequest request) {
        WaiterResponse waiter = waiterService.updateWaiter(id, request);
        return ResponseEntity.ok(ApiResponse.success("Waiter updated successfully", waiter));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete waiter", description = "Delete waiter by ID")
    public ResponseEntity<ApiResponse<Void>> deleteWaiter(@PathVariable Long id) {
        waiterService.deleteWaiter(id);
        return ResponseEntity.ok(ApiResponse.success("Waiter deleted successfully", null));
    }

    @PostMapping("/{waiterId}/tables/{tableId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HEAD_WAITER')")
    @Operation(summary = "Assign waiter to table", description = "Assign waiter to a specific table")
    public ResponseEntity<ApiResponse<Void>> assignToTable(
            @PathVariable Long waiterId,
            @PathVariable Long tableId) {
        waiterService.assignToTable(waiterId, tableId);
        return ResponseEntity.ok(ApiResponse.success("Waiter assigned to table successfully", null));
    }

    @PostMapping("/{waiterId}/tables/{tableId}/unassign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HEAD_WAITER')")
    @Operation(summary = "Unassign waiter from table", description = "Unassign waiter from a specific table")
    public ResponseEntity<ApiResponse<Void>> unassignFromTable(
            @PathVariable Long waiterId,
            @PathVariable Long tableId) {
        waiterService.unassignFromTable(waiterId, tableId);
        return ResponseEntity.ok(ApiResponse.success("Waiter unassigned from table successfully", null));
    }
}
