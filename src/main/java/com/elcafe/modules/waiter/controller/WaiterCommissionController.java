package com.elcafe.modules.waiter.controller;

import com.elcafe.modules.waiter.dto.CommissionConfigRequest;
import com.elcafe.modules.waiter.dto.WaiterCommissionDTO;
import com.elcafe.modules.waiter.dto.WaiterCommissionSummaryDTO;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterCommission;
import com.elcafe.modules.waiter.service.WaiterCommissionService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/waiter-commissions")
@RequiredArgsConstructor
@Tag(name = "Waiter Commission", description = "Waiter commission management endpoints")
public class WaiterCommissionController {

    private final WaiterCommissionService commissionService;

    /**
     * Configure commission settings for a waiter
     */
    @PutMapping("/waiter/{waiterId}/config")
    @Operation(summary = "Configure commission settings for a waiter")
    public ResponseEntity<ApiResponse<Waiter>> configureCommission(
            @PathVariable Long waiterId,
            @Valid @RequestBody CommissionConfigRequest request) {
        Waiter waiter = commissionService.updateCommissionConfig(waiterId, request);
        return ResponseEntity.ok(ApiResponse.success(waiter,
                "Commission configuration updated successfully"));
    }

    /**
     * Get commission summary for a waiter within date range
     */
    @GetMapping("/waiter/{waiterId}/summary")
    @Operation(summary = "Get commission summary for a waiter")
    public ResponseEntity<ApiResponse<WaiterCommissionSummaryDTO>> getCommissionSummary(
            @PathVariable Long waiterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        WaiterCommissionSummaryDTO summary = commissionService.getCommissionSummary(waiterId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    /**
     * Get commission history for a waiter (paginated)
     */
    @GetMapping("/waiter/{waiterId}/history")
    @Operation(summary = "Get commission history for a waiter")
    public ResponseEntity<ApiResponse<Page<WaiterCommissionDTO>>> getCommissionHistory(
            @PathVariable Long waiterId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<WaiterCommissionDTO> commissions = commissionService.getCommissionHistory(waiterId, pageable);
        return ResponseEntity.ok(ApiResponse.success(commissions));
    }

    /**
     * Get all commissions for a restaurant (paginated)
     */
    @GetMapping("/restaurant/{restaurantId}")
    @Operation(summary = "Get all commissions for a restaurant")
    public ResponseEntity<ApiResponse<Page<WaiterCommissionDTO>>> getRestaurantCommissions(
            @PathVariable Long restaurantId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<WaiterCommissionDTO> commissions = commissionService.getRestaurantCommissions(restaurantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(commissions));
    }

    /**
     * Get commission report for all waiters in a restaurant
     */
    @GetMapping("/restaurant/{restaurantId}/report")
    @Operation(summary = "Get commission report for all waiters in a restaurant")
    public ResponseEntity<ApiResponse<List<WaiterCommissionSummaryDTO>>> getRestaurantCommissionReport(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<WaiterCommissionSummaryDTO> report = commissionService.getRestaurantCommissionReport(
                restaurantId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * Approve pending commissions for payment
     */
    @PostMapping("/approve")
    @Operation(summary = "Approve pending commissions for payment")
    public ResponseEntity<ApiResponse<List<WaiterCommission>>> approveCommissions(
            @RequestBody List<Long> commissionIds) {
        List<WaiterCommission> approved = commissionService.approveCommissions(commissionIds);
        return ResponseEntity.ok(ApiResponse.success(approved,
                "Approved " + approved.size() + " commissions"));
    }

    /**
     * Get quick stats for commission dashboard
     */
    @GetMapping("/waiter/{waiterId}/quick-stats")
    @Operation(summary = "Get quick commission stats for today, this week, this month")
    public ResponseEntity<ApiResponse<Map<String, WaiterCommissionSummaryDTO>>> getQuickStats(
            @PathVariable Long waiterId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate monthStart = today.withDayOfMonth(1);

        Map<String, WaiterCommissionSummaryDTO> stats = Map.of(
                "today", commissionService.getCommissionSummary(waiterId, today, today),
                "thisWeek", commissionService.getCommissionSummary(waiterId, weekStart, today),
                "thisMonth", commissionService.getCommissionSummary(waiterId, monthStart, today)
        );

        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
