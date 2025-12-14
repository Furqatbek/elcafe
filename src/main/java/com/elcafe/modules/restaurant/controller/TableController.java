package com.elcafe.modules.restaurant.controller;

import com.elcafe.modules.restaurant.dto.CreateTableRequest;
import com.elcafe.modules.restaurant.dto.TableResponse;
import com.elcafe.modules.restaurant.dto.UpdateTableRequest;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.service.TableService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Restaurant Tables", description = "Table management endpoints for restaurants")
@SecurityRequirement(name = "Bearer Authentication")
public class TableController {

    private final TableService tableService;

    @PostMapping("/tables")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Create a new table", description = "Create a new table for a restaurant")
    public ResponseEntity<ApiResponse<TableResponse>> createTable(
            @Valid @RequestBody CreateTableRequest request) {
        log.info("Creating table {} for restaurant {}", request.getTableNumber(), request.getRestaurantId());

        TableResponse response = tableService.createTable(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Table created successfully", response));
    }

    @PutMapping("/tables/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Update a table", description = "Update table information")
    public ResponseEntity<ApiResponse<TableResponse>> updateTable(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTableRequest request) {
        log.info("Updating table: {}", id);

        TableResponse response = tableService.updateTable(id, request);
        return ResponseEntity.ok(ApiResponse.success("Table updated successfully", response));
    }

    @GetMapping("/tables/{id}")
    @Operation(summary = "Get table by ID", description = "Get detailed information about a specific table")
    public ResponseEntity<ApiResponse<TableResponse>> getTableById(@PathVariable Long id) {
        log.info("Getting table: {}", id);

        TableResponse response = tableService.getTableById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/restaurants/{restaurantId}/tables")
    @Operation(summary = "Get all tables for a restaurant", description = "Get all tables for a specific restaurant")
    public ResponseEntity<ApiResponse<List<TableResponse>>> getTablesByRestaurant(
            @PathVariable Long restaurantId) {
        log.info("Getting tables for restaurant: {}", restaurantId);

        List<TableResponse> response = tableService.getTablesByRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/restaurants/{restaurantId}/tables/available")
    @Operation(summary = "Get available tables", description = "Get all available tables for a restaurant")
    public ResponseEntity<ApiResponse<List<TableResponse>>> getAvailableTables(
            @PathVariable Long restaurantId) {
        log.info("Getting available tables for restaurant: {}", restaurantId);

        List<TableResponse> response = tableService.getAvailableTables(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/restaurants/{restaurantId}/tables/sections")
    @Operation(summary = "Get table sections", description = "Get all unique sections for a restaurant")
    public ResponseEntity<ApiResponse<List<String>>> getSections(@PathVariable Long restaurantId) {
        log.info("Getting sections for restaurant: {}", restaurantId);

        List<String> response = tableService.getSections(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/restaurants/{restaurantId}/tables/section/{section}")
    @Operation(summary = "Get tables by section", description = "Get all tables in a specific section")
    public ResponseEntity<ApiResponse<List<TableResponse>>> getTablesBySection(
            @PathVariable Long restaurantId,
            @PathVariable String section) {
        log.info("Getting tables for restaurant {} in section {}", restaurantId, section);

        List<TableResponse> response = tableService.getTablesBySection(restaurantId, section);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/tables/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @Operation(summary = "Update table status", description = "Update the status of a table")
    public ResponseEntity<ApiResponse<TableResponse>> updateTableStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> statusUpdate) {
        log.info("Updating table {} status", id);

        RestaurantTable.TableStatus status = RestaurantTable.TableStatus.valueOf(statusUpdate.get("status"));
        TableResponse response = tableService.updateTableStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Table status updated successfully", response));
    }

    @DeleteMapping("/tables/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a table", description = "Delete a table from the system")
    public ResponseEntity<ApiResponse<Void>> deleteTable(@PathVariable Long id) {
        log.info("Deleting table: {}", id);

        tableService.deleteTable(id);
        return ResponseEntity.ok(ApiResponse.success("Table deleted successfully", null));
    }

    @GetMapping("/restaurants/{restaurantId}/tables/stats")
    @Operation(summary = "Get table statistics", description = "Get statistics about tables for a restaurant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTableStats(@PathVariable Long restaurantId) {
        log.info("Getting table stats for restaurant: {}", restaurantId);

        long totalTables = tableService.getTablesByRestaurant(restaurantId).size();
        long availableTables = tableService.countTablesByStatus(restaurantId, RestaurantTable.TableStatus.AVAILABLE);
        long occupiedTables = tableService.countTablesByStatus(restaurantId, RestaurantTable.TableStatus.OCCUPIED);
        long reservedTables = tableService.countTablesByStatus(restaurantId, RestaurantTable.TableStatus.RESERVED);

        Map<String, Object> stats = Map.of(
                "totalTables", totalTables,
                "availableTables", availableTables,
                "occupiedTables", occupiedTables,
                "reservedTables", reservedTables
        );

        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
