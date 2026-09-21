package com.elcafe.modules.loyalty.controller;

import com.elcafe.modules.loyalty.dto.CustomerMilestoneProgressResponse;
import com.elcafe.modules.loyalty.dto.MilestoneCreateRequest;
import com.elcafe.modules.loyalty.dto.MilestoneResponse;
import com.elcafe.modules.loyalty.dto.MilestoneUpdateRequest;
import com.elcafe.modules.loyalty.service.MilestoneService;
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

@Slf4j
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Loyalty Milestones", description = "Stamp-card style milestone rewards (e.g., visit 10 times, get 1 free)")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    // --- Admin/Manager endpoints for managing milestones ---

    @PostMapping("/restaurants/{restaurantId}/milestones")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Create milestone", description = "Create a new stamp-card milestone for a restaurant")
    public ResponseEntity<ApiResponse<MilestoneResponse>> createMilestone(
            @PathVariable Long restaurantId,
            @Valid @RequestBody MilestoneCreateRequest request) {
        log.info("Creating milestone for restaurant {}", restaurantId);
        MilestoneResponse response = milestoneService.createMilestone(restaurantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Milestone created successfully", response));
    }

    @PutMapping("/milestones/{milestoneId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Update milestone", description = "Update an existing milestone")
    public ResponseEntity<ApiResponse<MilestoneResponse>> updateMilestone(
            @PathVariable Long milestoneId,
            @Valid @RequestBody MilestoneUpdateRequest request) {
        log.info("Updating milestone {}", milestoneId);
        MilestoneResponse response = milestoneService.updateMilestone(milestoneId, request);
        return ResponseEntity.ok(ApiResponse.success("Milestone updated successfully", response));
    }

    @GetMapping("/milestones/{milestoneId}")
    @Operation(summary = "Get milestone", description = "Get milestone details by ID")
    public ResponseEntity<ApiResponse<MilestoneResponse>> getMilestone(@PathVariable Long milestoneId) {
        MilestoneResponse response = milestoneService.getMilestone(milestoneId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/restaurants/{restaurantId}/milestones")
    @Operation(summary = "Get restaurant milestones", description = "Get all milestones for a restaurant")
    public ResponseEntity<ApiResponse<List<MilestoneResponse>>> getRestaurantMilestones(
            @PathVariable Long restaurantId) {
        List<MilestoneResponse> response = milestoneService.getMilestonesByRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/milestones/{milestoneId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete milestone", description = "Delete a milestone")
    public ResponseEntity<Void> deleteMilestone(@PathVariable Long milestoneId) {
        log.info("Deleting milestone {}", milestoneId);
        milestoneService.deleteMilestone(milestoneId);
        return ResponseEntity.noContent().build();
    }

    // --- Customer-facing endpoints for tracking progress ---

    @GetMapping("/restaurants/{restaurantId}/milestones/customers/{customerId}/progress")
    @Operation(summary = "Get customer milestone progress", description = "Get a customer's progress on all active milestones at a restaurant")
    public ResponseEntity<ApiResponse<List<CustomerMilestoneProgressResponse>>> getCustomerProgress(
            @PathVariable Long restaurantId,
            @PathVariable Long customerId) {
        List<CustomerMilestoneProgressResponse> response = milestoneService.getCustomerProgress(customerId, restaurantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/milestones/customers/{customerId}/pending-rewards")
    @Operation(summary = "Get pending rewards", description = "Get all pending milestone rewards for a customer")
    public ResponseEntity<ApiResponse<List<CustomerMilestoneProgressResponse>>> getPendingRewards(
            @PathVariable Long customerId) {
        List<CustomerMilestoneProgressResponse> response = milestoneService.getPendingRewards(customerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/milestones/{milestoneId}/customers/{customerId}/redeem")
    @Operation(summary = "Redeem milestone reward", description = "Redeem a pending milestone reward")
    public ResponseEntity<ApiResponse<CustomerMilestoneProgressResponse>> redeemReward(
            @PathVariable Long milestoneId,
            @PathVariable Long customerId) {
        log.info("Customer {} redeeming milestone {} reward", customerId, milestoneId);
        CustomerMilestoneProgressResponse response = milestoneService.redeemReward(customerId, milestoneId);
        return ResponseEntity.ok(ApiResponse.success("Reward redeemed successfully", response));
    }
}
