package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.AddOnResponse;
import com.elcafe.modules.menu.dto.CreateAddOnRequest;
import com.elcafe.modules.menu.dto.UpdateAddOnRequest;
import com.elcafe.modules.menu.service.AddOnService;
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
@RequestMapping("/api/v1/addon-groups/{addOnGroupId}/addons")
@RequiredArgsConstructor
@Tag(name = "Add-Ons", description = "Add-On management APIs")
public class AddOnController {

    private final AddOnService addOnService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get all add-ons", description = "Get all add-ons for an add-on group")
    public ResponseEntity<ApiResponse<List<AddOnResponse>>> getAllAddOns(
            @PathVariable Long addOnGroupId,
            @RequestParam(required = false, defaultValue = "false") boolean availableOnly
    ) {
        log.info("Fetching add-ons for group: {}, availableOnly: {}", addOnGroupId, availableOnly);

        List<AddOnResponse> addOns = availableOnly
                ? addOnService.getAvailableAddOnsByGroup(addOnGroupId)
                : addOnService.getAllAddOnsByGroup(addOnGroupId);

        return ResponseEntity.ok(ApiResponse.success("Add-ons retrieved successfully", addOns));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get add-on by ID", description = "Get a single add-on by its ID")
    public ResponseEntity<ApiResponse<AddOnResponse>> getAddOnById(
            @PathVariable Long addOnGroupId,
            @PathVariable Long id
    ) {
        log.info("Fetching add-on: {} for group: {}", id, addOnGroupId);

        AddOnResponse addOn = addOnService.getAddOnById(addOnGroupId, id);

        return ResponseEntity.ok(ApiResponse.success("Add-on retrieved successfully", addOn));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create add-on", description = "Create a new add-on for an add-on group")
    public ResponseEntity<ApiResponse<AddOnResponse>> createAddOn(
            @PathVariable Long addOnGroupId,
            @Valid @RequestBody CreateAddOnRequest request
    ) {
        log.info("Creating add-on: {} for group: {}", request.getName(), addOnGroupId);

        // Ensure the addOnGroupId in the path matches the one in the request
        if (!addOnGroupId.equals(request.getAddOnGroupId())) {
            throw new IllegalArgumentException("AddOnGroup ID in path does not match request body");
        }

        AddOnResponse addOn = addOnService.createAddOn(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Add-on created successfully", addOn));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update add-on", description = "Update an existing add-on")
    public ResponseEntity<ApiResponse<AddOnResponse>> updateAddOn(
            @PathVariable Long addOnGroupId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateAddOnRequest request
    ) {
        log.info("Updating add-on: {} for group: {}", id, addOnGroupId);

        AddOnResponse addOn = addOnService.updateAddOn(addOnGroupId, id, request);

        return ResponseEntity.ok(ApiResponse.success("Add-on updated successfully", addOn));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete add-on", description = "Delete an add-on")
    public ResponseEntity<ApiResponse<Void>> deleteAddOn(
            @PathVariable Long addOnGroupId,
            @PathVariable Long id
    ) {
        log.info("Deleting add-on: {} for group: {}", id, addOnGroupId);

        addOnService.deleteAddOn(addOnGroupId, id);

        return ResponseEntity.ok(ApiResponse.success("Add-on deleted successfully", null));
    }
}
