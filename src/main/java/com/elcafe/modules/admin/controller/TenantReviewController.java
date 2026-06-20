package com.elcafe.modules.admin.controller;

import com.elcafe.modules.admin.service.TenantReviewService;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * SUPER_ADMIN-only review of low-confidence heuristic tenant assignments (Phase 0 §3.7). Cross-tenant
 * by design — only the platform operator may see and correct assignments across restaurants, so the
 * Hibernate tenant filter is intentionally inactive here (null tenant scope for SUPER_ADMIN).
 */
@RestController
@RequestMapping("/api/v1/admin/tenant-review")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Tenant Review", description = "Platform review of low-confidence backfill tenant assignments")
@SecurityRequirement(name = "bearerAuth")
public class TenantReviewController {

    private final TenantReviewService tenantReviewService;

    @GetMapping("/customers")
    @Operation(summary = "List customers needing tenant review",
            description = "Customers whose restaurant was assigned by the no-evidence backfill fallback")
    public ResponseEntity<ApiResponse<Page<Map<String, Object>>>> lowConfidenceCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> result = tenantReviewService
                .lowConfidenceCustomers(PageRequest.of(page, size))
                .map(TenantReviewController::toCustomerView);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/waiters")
    @Operation(summary = "List waiters needing tenant review")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> lowConfidenceWaiters() {
        List<Map<String, Object>> result = tenantReviewService.lowConfidenceWaiters().stream()
                .map(TenantReviewController::toWaiterView).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/customers/{id}/reassign")
    @Operation(summary = "Reassign a customer to a restaurant (marks the assignment reviewed)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reassignCustomer(
            @PathVariable Long id, @Valid @RequestBody ReassignRequest request) {
        Customer updated = tenantReviewService.reassignCustomer(id, request.restaurantId());
        return ResponseEntity.ok(ApiResponse.success("Customer reassigned", toCustomerView(updated)));
    }

    @PostMapping("/waiters/{id}/reassign")
    @Operation(summary = "Reassign a waiter to a restaurant (marks the assignment reviewed)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reassignWaiter(
            @PathVariable Long id, @Valid @RequestBody ReassignRequest request) {
        Waiter updated = tenantReviewService.reassignWaiter(id, request.restaurantId());
        return ResponseEntity.ok(ApiResponse.success("Waiter reassigned", toWaiterView(updated)));
    }

    private static Map<String, Object> toCustomerView(Customer c) {
        String name = ((c.getFirstName() != null ? c.getFirstName() : "") + " "
                + (c.getLastName() != null ? c.getLastName() : "")).trim();
        return Map.of(
                "id", c.getId(),
                "name", name,
                "phone", c.getPhone() != null ? c.getPhone() : "",
                "restaurantId", c.getRestaurantId(),
                "confidence", c.getTenantAssignmentConfidence().name()
        );
    }

    private static Map<String, Object> toWaiterView(Waiter w) {
        return Map.of(
                "id", w.getId(),
                "name", w.getName() != null ? w.getName() : "",
                "restaurantId", w.getRestaurantId(),
                "confidence", w.getTenantAssignmentConfidence().name()
        );
    }

    /** Reassignment payload — the restaurant the row truly belongs to. */
    public record ReassignRequest(@NotNull Long restaurantId) {}
}
