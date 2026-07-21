package com.elcafe.modules.billing.controller;

import com.elcafe.modules.billing.dto.BillingStatusDto;
import com.elcafe.modules.billing.dto.PlanSummaryDto;
import com.elcafe.modules.billing.dto.SetPlanRequest;
import com.elcafe.modules.billing.service.PlanGateService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.security.UserPrincipal;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Subscription plan info and admin plan management")
public class SubscriptionController {

    private final PlanGateService planGateService;
    private final RestaurantAuthorizationService authorizationService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Current plan", description = "The caller's restaurant plan, features, and expiry/read-only state")
    public ResponseEntity<ApiResponse<BillingStatusDto>> me(@AuthenticationPrincipal UserPrincipal user) {
        Long restaurantId = user != null ? user.getRestaurantId() : null;
        return ResponseEntity.ok(ApiResponse.success(planGateService.getBillingStatus(restaurantId)));
    }

    @GetMapping("/plans")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Available plans", description = "The active plan catalogue, ordered for display")
    public ResponseEntity<ApiResponse<List<PlanSummaryDto>>> plans() {
        return ResponseEntity.ok(ApiResponse.success(planGateService.listActivePlans()));
    }

    @PostMapping("/admin/set-plan")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Operation(summary = "Set a restaurant's plan", description = "Owner/admin self-serve: change the caller's own restaurant plan; audited")
    public ResponseEntity<ApiResponse<BillingStatusDto>> setPlan(
            @Valid @RequestBody SetPlanRequest request,
            @AuthenticationPrincipal UserPrincipal actor) {
        // Ownership: a tenant ADMIN/OWNER may only set its OWN restaurant's plan. The restaurantId
        // travels in the body, so the role check alone would let tenant A rewrite tenant B's plan/expiry (a
        // cross-tenant DoS). validateRestaurantAccess always-enforces (SUPER_ADMIN bypasses; the
        // cross-tenant platform path is the separate SUPER_ADMIN-gated PlatformAdminController).
        authorizationService.validateRestaurantAccess(request.getRestaurantId());
        log.info("Admin set-plan: restaurant={}, plan={}", request.getRestaurantId(), request.getPlanCode());
        BillingStatusDto status = planGateService.setPlan(request, actor);
        return ResponseEntity.ok(ApiResponse.success("Plan updated", status));
    }
}
