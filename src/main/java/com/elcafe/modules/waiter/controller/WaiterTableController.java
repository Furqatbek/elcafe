package com.elcafe.modules.waiter.controller;

import com.elcafe.modules.waiter.dto.AssignWaiterRequest;
import com.elcafe.modules.waiter.dto.CreateTableRequest;
import com.elcafe.modules.waiter.dto.TableMergeRequest;
import com.elcafe.modules.waiter.dto.TableResponse;
import com.elcafe.modules.waiter.dto.UpdateTableRequest;
import com.elcafe.modules.waiter.enums.TableStatus;
import com.elcafe.modules.waiter.service.TableService;
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
 * Controller for table management
 */
@RestController
@RequestMapping("/api/v1/waiter/tables")
@RequiredArgsConstructor
@Tag(name = "Waiter Tables", description = "Table management endpoints for waiters")
@SecurityRequirement(name = "bearerAuth")
public class WaiterTableController {

    private final TableService tableService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SUPERVISOR', 'WAITER')")
    @Operation(summary = "Get all tables", description = "Get paginated list of all tables")
    public ResponseEntity<ApiResponse<Page<TableResponse>>> getAllTables(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "number") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TableResponse> tables = tableService.getAllTables(pageable);

        return ResponseEntity.ok(ApiResponse.success("Tables retrieved successfully", tables));
    }

    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SUPERVISOR', 'WAITER')")
    @Operation(summary = "Get available tables", description = "Get list of available tables")
    public ResponseEntity<ApiResponse<List<TableResponse>>> getAvailableTables() {
        List<TableResponse> tables = tableService.getAvailableTables();
        return ResponseEntity.ok(ApiResponse.success("Available tables retrieved successfully", tables));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SUPERVISOR', 'WAITER')")
    @Operation(summary = "Get tables by status", description = "Get tables filtered by status")
    public ResponseEntity<ApiResponse<List<TableResponse>>> getTablesByStatus(
            @PathVariable TableStatus status) {
        List<TableResponse> tables = tableService.getByStatus(status);
        return ResponseEntity.ok(ApiResponse.success("Tables retrieved successfully", tables));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SUPERVISOR', 'WAITER')")
    @Operation(summary = "Get table by ID", description = "Get table details by ID")
    public ResponseEntity<ApiResponse<TableResponse>> getTableById(@PathVariable Long id) {
        TableResponse table = tableService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Table retrieved successfully", table));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Create table", description = "Create a new table")
    public ResponseEntity<ApiResponse<TableResponse>> createTable(
            @Valid @RequestBody CreateTableRequest request) {
        TableResponse table = tableService.createTable(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Table created successfully", table));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Update table", description = "Update existing table")
    public ResponseEntity<ApiResponse<TableResponse>> updateTable(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTableRequest request) {
        TableResponse table = tableService.updateTable(id, request);
        return ResponseEntity.ok(ApiResponse.success("Table updated successfully", table));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete table", description = "Delete table by ID")
    public ResponseEntity<ApiResponse<Void>> deleteTable(@PathVariable Long id) {
        tableService.deleteTable(id);
        return ResponseEntity.ok(ApiResponse.success("Table deleted successfully", null));
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'WAITER')")
    @Operation(summary = "Open table", description = "Open table for service")
    public ResponseEntity<ApiResponse<TableResponse>> openTable(
            @PathVariable Long id,
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        TableResponse table = tableService.openTable(id, waiterId);
        return ResponseEntity.ok(ApiResponse.success("Table opened successfully", table));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'WAITER')")
    @Operation(summary = "Close table", description = "Close table after service")
    public ResponseEntity<ApiResponse<TableResponse>> closeTable(@PathVariable Long id) {
        TableResponse table = tableService.closeTable(id);
        return ResponseEntity.ok(ApiResponse.success("Table closed successfully", table));
    }

    @PostMapping("/{sourceId}/merge")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'HEAD_WAITER', 'WAITER')")
    @Operation(summary = "Merge tables", description = "Merge source table into target table")
    public ResponseEntity<ApiResponse<TableResponse>> mergeTables(
            @PathVariable Long sourceId,
            @Valid @RequestBody TableMergeRequest request,
            @RequestHeader("X-Waiter-Name") String waiterName) {
        TableResponse table = tableService.mergeTables(sourceId, request.getTargetTableId(), waiterName);
        return ResponseEntity.ok(ApiResponse.success("Tables merged successfully", table));
    }

    @PostMapping("/{id}/unmerge")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'HEAD_WAITER', 'WAITER')")
    @Operation(summary = "Unmerge table", description = "Unmerge a merged table")
    public ResponseEntity<ApiResponse<TableResponse>> unmergeTables(@PathVariable Long id) {
        TableResponse table = tableService.unmergeTables(id);
        return ResponseEntity.ok(ApiResponse.success("Table unmerged successfully", table));
    }

    @PostMapping("/{tableId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HEAD_WAITER')")
    @Operation(summary = "Assign waiter to table", description = "Assign a waiter to a table")
    public ResponseEntity<ApiResponse<TableResponse>> assignWaiter(
            @PathVariable Long tableId,
            @Valid @RequestBody AssignWaiterRequest request) {
        TableResponse table = tableService.assignWaiter(tableId, request.getWaiterId());
        return ResponseEntity.ok(ApiResponse.success("Waiter assigned successfully", table));
    }

    @PostMapping("/{tableId}/unassign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HEAD_WAITER')")
    @Operation(summary = "Unassign waiter from table", description = "Unassign current waiter from table")
    public ResponseEntity<ApiResponse<TableResponse>> unassignWaiter(@PathVariable Long tableId) {
        TableResponse table = tableService.unassignWaiter(tableId);
        return ResponseEntity.ok(ApiResponse.success("Waiter unassigned successfully", table));
    }

    @PutMapping("/{tableId}/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'WAITER')")
    @Operation(summary = "Update table status", description = "Update table status directly")
    public ResponseEntity<ApiResponse<TableResponse>> updateTableStatus(
            @PathVariable Long tableId,
            @PathVariable TableStatus status) {
        TableResponse table = tableService.updateStatus(tableId, status);
        return ResponseEntity.ok(ApiResponse.success("Table status updated successfully", table));
    }
}
