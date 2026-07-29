package com.elcafe.modules.restaurant.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.restaurant.dto.CreateWorkingHoursRequest;
import com.elcafe.modules.restaurant.dto.UpdateWorkingHoursRequest;
import com.elcafe.modules.restaurant.dto.WorkingHoursResponse;
import com.elcafe.modules.restaurant.service.WorkingHoursService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Working Hours", description = "Employee working hours and shift management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
// Internal employee-schedule data — staff only. The read endpoints carried no role gate, so any
// authenticated principal could read staff working hours (the /users/{userId}/... reads outright, and
// the /restaurants/{restaurantId}/... reads too, since restaurant checkAccess passes for a consumer
// bound to that tenant). This class default closes every read; the write methods below override it with
// their own tighter ADMIN/MANAGER gates (a method-level @PreAuthorize takes precedence over the class).
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'OPERATOR', 'CASHIER', 'WAITER', 'KITCHEN_STAFF')")
public class WorkingHoursController {

    private final WorkingHoursService workingHoursService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping("/restaurants/{restaurantId}/working-hours")
    @Operation(summary = "List working hours by restaurant", description = "Get all working hours for a specific restaurant")
    public ResponseEntity<ApiResponse<List<WorkingHoursResponse>>> getWorkingHoursByRestaurant(
            @PathVariable Long restaurantId
    ) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<WorkingHoursResponse> response = workingHoursService.getAllByRestaurantId(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{userId}/working-hours")
    @Operation(summary = "List working hours by user", description = "Get all working hours for a specific employee")
    public ResponseEntity<ApiResponse<List<WorkingHoursResponse>>> getWorkingHoursByUser(
            @PathVariable Long userId
    ) {
        List<WorkingHoursResponse> response = workingHoursService.getAllByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/restaurants/{restaurantId}/users/{userId}/working-hours")
    @Operation(summary = "List working hours by restaurant and user", description = "Get all working hours for a specific employee at a specific restaurant")
    public ResponseEntity<ApiResponse<List<WorkingHoursResponse>>> getWorkingHoursByRestaurantAndUser(
            @PathVariable Long restaurantId,
            @PathVariable Long userId
    ) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<WorkingHoursResponse> response = workingHoursService.getAllByRestaurantAndUser(restaurantId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/restaurants/{restaurantId}/working-hours/day/{dayOfWeek}")
    @Operation(summary = "List working hours by day", description = "Get all working hours for a specific restaurant on a specific day")
    public ResponseEntity<ApiResponse<List<WorkingHoursResponse>>> getWorkingHoursByDay(
            @PathVariable Long restaurantId,
            @PathVariable @Parameter(description = "Day of week (e.g., MONDAY, TUESDAY)", example = "MONDAY") DayOfWeek dayOfWeek
    ) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<WorkingHoursResponse> response = workingHoursService.getAllByDay(restaurantId, dayOfWeek);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/working-hours/{id}")
    @Operation(summary = "Get working hours", description = "Get working hours by ID")
    public ResponseEntity<ApiResponse<WorkingHoursResponse>> getWorkingHours(
            @PathVariable Long id
    ) {
        WorkingHoursResponse response = workingHoursService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/working-hours")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Create working hours", description = "Create new working hours for an employee (Admin/Manager only)")
    public ResponseEntity<ApiResponse<WorkingHoursResponse>> createWorkingHours(
            @Valid @RequestBody CreateWorkingHoursRequest request
    ) {
        WorkingHoursResponse response = workingHoursService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Working hours created successfully", response));
    }

    @PutMapping("/working-hours/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Update working hours", description = "Update working hours details (Admin/Manager only)")
    public ResponseEntity<ApiResponse<WorkingHoursResponse>> updateWorkingHours(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWorkingHoursRequest request
    ) {
        WorkingHoursResponse response = workingHoursService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Working hours updated successfully", response));
    }

    @DeleteMapping("/working-hours/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete working hours", description = "Delete working hours (Admin/Manager only)")
    public ResponseEntity<ApiResponse<Void>> deleteWorkingHours(
            @PathVariable Long id
    ) {
        workingHoursService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Working hours deleted successfully", null));
    }

    @DeleteMapping("/restaurants/{restaurantId}/working-hours")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete all working hours for restaurant", description = "Delete all working hours for a specific restaurant (Admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteAllWorkingHoursByRestaurant(
            @PathVariable Long restaurantId
    ) {
        restaurantAuthorizationService.checkAccess(restaurantId);

        workingHoursService.deleteAllByRestaurantId(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("All working hours deleted successfully for restaurant", null));
    }

    @DeleteMapping("/users/{userId}/working-hours")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete all working hours for user", description = "Delete all working hours for a specific employee (Admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteAllWorkingHoursByUser(
            @PathVariable Long userId
    ) {
        workingHoursService.deleteAllByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("All working hours deleted successfully for user", null));
    }
}
