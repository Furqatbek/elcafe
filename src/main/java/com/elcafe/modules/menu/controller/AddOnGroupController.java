package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.AddOnGroupResponse;
import com.elcafe.modules.menu.dto.CreateAddOnGroupRequest;
import com.elcafe.modules.menu.dto.UpdateAddOnGroupRequest;
import com.elcafe.modules.menu.service.AddOnGroupService;
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
@RequestMapping("/api/v1/restaurants/{restaurantId}/addon-groups")
@RequiredArgsConstructor
@Tag(name = "Add-On Groups", description = "Add-On Group management APIs")
public class AddOnGroupController {

    private final AddOnGroupService addOnGroupService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get all add-on groups", description = "Get all add-on groups for a restaurant")
    public ResponseEntity<ApiResponse<List<AddOnGroupResponse>>> getAllAddOnGroups(
            @PathVariable Long restaurantId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly
    ) {
        log.info("Fetching add-on groups for restaurant: {}, activeOnly: {}", restaurantId, activeOnly);

        List<AddOnGroupResponse> addOnGroups = activeOnly
                ? addOnGroupService.getActiveAddOnGroupsByRestaurant(restaurantId)
                : addOnGroupService.getAllAddOnGroupsByRestaurant(restaurantId);

        return ResponseEntity.ok(ApiResponse.success("Add-on groups retrieved successfully", addOnGroups));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get add-on group by ID", description = "Get a single add-on group by its ID")
    public ResponseEntity<ApiResponse<AddOnGroupResponse>> getAddOnGroupById(
            @PathVariable Long restaurantId,
            @PathVariable Long id
    ) {
        log.info("Fetching add-on group: {} for restaurant: {}", id, restaurantId);

        AddOnGroupResponse addOnGroup = addOnGroupService.getAddOnGroupById(restaurantId, id);

        return ResponseEntity.ok(ApiResponse.success("Add-on group retrieved successfully", addOnGroup));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create add-on group", description = "Create a new add-on group for a restaurant")
    public ResponseEntity<ApiResponse<AddOnGroupResponse>> createAddOnGroup(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateAddOnGroupRequest request
    ) {
        log.info("Creating add-on group: {} for restaurant: {}", request.getName(), restaurantId);

        // Ensure the restaurantId in the path matches the one in the request
        if (!restaurantId.equals(request.getRestaurantId())) {
            throw new IllegalArgumentException("Restaurant ID in path does not match request body");
        }

        AddOnGroupResponse addOnGroup = addOnGroupService.createAddOnGroup(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Add-on group created successfully", addOnGroup));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update add-on group", description = "Update an existing add-on group")
    public ResponseEntity<ApiResponse<AddOnGroupResponse>> updateAddOnGroup(
            @PathVariable Long restaurantId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateAddOnGroupRequest request
    ) {
        log.info("Updating add-on group: {} for restaurant: {}", id, restaurantId);

        AddOnGroupResponse addOnGroup = addOnGroupService.updateAddOnGroup(restaurantId, id, request);

        return ResponseEntity.ok(ApiResponse.success("Add-on group updated successfully", addOnGroup));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete add-on group", description = "Delete an add-on group")
    public ResponseEntity<ApiResponse<Void>> deleteAddOnGroup(
            @PathVariable Long restaurantId,
            @PathVariable Long id
    ) {
        log.info("Deleting add-on group: {} for restaurant: {}", id, restaurantId);

        addOnGroupService.deleteAddOnGroup(restaurantId, id);

        return ResponseEntity.ok(ApiResponse.success("Add-on group deleted successfully", null));
    }
}
