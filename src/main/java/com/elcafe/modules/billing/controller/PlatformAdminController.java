package com.elcafe.modules.billing.controller;

import com.elcafe.modules.billing.dto.BillingStatusDto;
import com.elcafe.modules.billing.dto.ChangePlanRequest;
import com.elcafe.modules.billing.dto.ExtendPlanRequest;
import com.elcafe.modules.billing.dto.TenantSummaryDto;
import com.elcafe.modules.billing.service.PlatformAdminService;
import com.elcafe.security.UserPrincipal;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * SUPER_ADMIN platform console — cross-tenant subscription management. The class-level
 * {@link PreAuthorize} restricts every endpoint to the platform-operator role (the only role allowed to
 * act across restaurants); per-tenant plan changes live on {@code /api/v1/billing/admin/**} instead.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platform admin", description = "SUPER_ADMIN cross-tenant subscription management")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;

    @GetMapping("/tenants")
    @Operation(summary = "List tenants", description = "All restaurants with their subscription state; optional name search")
    public ResponseEntity<ApiResponse<Page<TenantSummaryDto>>> listTenants(
            @RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(platformAdminService.listTenants(search, pageable)));
    }

    @PostMapping("/tenants/{id}/plan")
    @Operation(summary = "Change a tenant's plan")
    public ResponseEntity<ApiResponse<BillingStatusDto>> changePlan(
            @PathVariable Long id, @Valid @RequestBody ChangePlanRequest request,
            @AuthenticationPrincipal UserPrincipal actor) {
        log.info("Platform set-plan: restaurant={}, plan={}", id, request.getPlanCode());
        return ResponseEntity.ok(ApiResponse.success("Plan updated",
                platformAdminService.changePlan(id, request, actor)));
    }

    @PostMapping("/tenants/{id}/extend")
    @Operation(summary = "Extend a tenant's plan expiry by N days")
    public ResponseEntity<ApiResponse<BillingStatusDto>> extend(
            @PathVariable Long id, @Valid @RequestBody ExtendPlanRequest request,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok(ApiResponse.success("Plan extended",
                platformAdminService.extendPlan(id, request.getDays(), actor)));
    }

    @PostMapping("/tenants/{id}/suspend")
    @Operation(summary = "Suspend a tenant (active = false)")
    public ResponseEntity<ApiResponse<TenantSummaryDto>> suspend(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok(ApiResponse.success("Tenant suspended",
                platformAdminService.setActive(id, false, actor)));
    }

    @PostMapping("/tenants/{id}/reactivate")
    @Operation(summary = "Reactivate a tenant (active = true)")
    public ResponseEntity<ApiResponse<TenantSummaryDto>> reactivate(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok(ApiResponse.success("Tenant reactivated",
                platformAdminService.setActive(id, true, actor)));
    }

    @PostMapping("/tenants/{id}/cancel")
    @Operation(summary = "Cancel a tenant's subscription (status CANCELLED; access cut off)")
    public ResponseEntity<ApiResponse<TenantSummaryDto>> cancel(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok(ApiResponse.success("Subscription cancelled",
                platformAdminService.cancel(id, actor)));
    }
}
