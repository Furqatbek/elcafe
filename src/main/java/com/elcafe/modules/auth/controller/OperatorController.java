package com.elcafe.modules.auth.controller;

import com.elcafe.modules.auth.dto.CreateOperatorRequest;
import com.elcafe.modules.auth.dto.OperatorDTO;
import com.elcafe.modules.auth.dto.UpdateOperatorRequest;
import com.elcafe.modules.auth.service.OperatorService;
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
@RequestMapping("/api/v1/operators")
@RequiredArgsConstructor
@Tag(name = "Operators", description = "Operator management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class OperatorController {

    private final OperatorService operatorService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all operators", description = "Get paginated list of all operators")
    public ResponseEntity<ApiResponse<Page<OperatorDTO>>> getAllOperators(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<OperatorDTO> operators = operatorService.getAllOperators(pageable);

        return ResponseEntity.ok(ApiResponse.success(operators, "Operators retrieved successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get operator by ID", description = "Get operator details by ID")
    public ResponseEntity<ApiResponse<OperatorDTO>> getOperatorById(@PathVariable Long id) {
        OperatorDTO operator = operatorService.getOperatorById(id);
        return ResponseEntity.ok(ApiResponse.success(operator, "Operator retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create operator", description = "Create a new operator")
    public ResponseEntity<ApiResponse<OperatorDTO>> createOperator(
            @Valid @RequestBody CreateOperatorRequest request) {
        OperatorDTO operator = operatorService.createOperator(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(operator, "Operator created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update operator", description = "Update existing operator")
    public ResponseEntity<ApiResponse<OperatorDTO>> updateOperator(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOperatorRequest request) {
        OperatorDTO operator = operatorService.updateOperator(id, request);
        return ResponseEntity.ok(ApiResponse.success(operator, "Operator updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete operator", description = "Delete operator by ID")
    public ResponseEntity<ApiResponse<Void>> deleteOperator(@PathVariable Long id) {
        operatorService.deleteOperator(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Operator deleted successfully"));
    }
}
