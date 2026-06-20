package com.elcafe.modules.waiter.controller;

import com.elcafe.modules.waiter.dto.WaiterKPIConfigRequest;
import com.elcafe.modules.waiter.dto.WaiterLeaderboardEntry;
import com.elcafe.modules.waiter.dto.WaiterPerformanceSummary;
import com.elcafe.modules.waiter.entity.WaiterKPIConfig;
import com.elcafe.modules.waiter.entity.WaiterPerformance;
import com.elcafe.modules.waiter.service.WaiterPerformanceService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/waiter-performance")
@RequiredArgsConstructor
@Tag(name = "Waiter Performance", description = "Waiter KPI configuration and performance tracking")
public class WaiterPerformanceController {

    private final WaiterPerformanceService performanceService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    // ==================== KPI Configuration ====================

    @GetMapping("/restaurant/{restaurantId}/kpi-configs")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Get all KPI configs", description = "Get all KPI configurations for a restaurant")
    public ResponseEntity<List<WaiterKPIConfig>> getKPIConfigs(@PathVariable Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(performanceService.getKPIConfigs(restaurantId));
    }

    @GetMapping("/waiter/{waiterId}/kpi-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @Operation(summary = "Get effective KPI config", description = "Get effective KPI configuration for a waiter")
    public ResponseEntity<WaiterKPIConfig> getEffectiveKPIConfig(
            @PathVariable Long waiterId,
            @RequestParam Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(performanceService.getEffectiveKPIConfig(waiterId, restaurantId));
    }

    @PostMapping("/restaurant/{restaurantId}/kpi-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Create/update KPI config", description = "Create or update KPI configuration")
    public ResponseEntity<WaiterKPIConfig> saveKPIConfig(
            @PathVariable Long restaurantId,
            @RequestBody WaiterKPIConfigRequest request) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(performanceService.saveKPIConfig(restaurantId, request));
    }

    @DeleteMapping("/kpi-config/{configId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete KPI config", description = "Delete a KPI configuration")
    public ResponseEntity<Map<String, Object>> deleteKPIConfig(@PathVariable Long configId) {
        performanceService.deleteKPIConfig(configId);
        return ResponseEntity.ok(Map.of("success", true, "message", "KPI config deleted"));
    }

    // ==================== Performance Tracking ====================

    @GetMapping("/waiter/{waiterId}/today")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @Operation(summary = "Get today's performance", description = "Get waiter's performance for today")
    public ResponseEntity<WaiterPerformance> getTodayPerformance(@PathVariable Long waiterId) {
        return performanceService.getTodayPerformance(waiterId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/waiter/{waiterId}/date/{date}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @Operation(summary = "Get performance for date", description = "Get waiter's performance for a specific date")
    public ResponseEntity<WaiterPerformance> getPerformance(
            @PathVariable Long waiterId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return performanceService.getPerformance(waiterId, date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/waiter/{waiterId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @Operation(summary = "Get performance history", description = "Get paginated performance history for a waiter")
    public ResponseEntity<Page<WaiterPerformance>> getPerformanceHistory(
            @PathVariable Long waiterId,
            Pageable pageable) {
        return ResponseEntity.ok(performanceService.getPerformanceHistory(waiterId, pageable));
    }

    @GetMapping("/waiter/{waiterId}/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @Operation(summary = "Get performance summary", description = "Get aggregated performance summary for a date range")
    public ResponseEntity<WaiterPerformanceSummary> getPerformanceSummary(
            @PathVariable Long waiterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(performanceService.getPerformanceSummary(waiterId, startDate, endDate));
    }

    @GetMapping("/restaurant/{restaurantId}/leaderboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @Operation(summary = "Get leaderboard", description = "Get waiter leaderboard for a restaurant")
    public ResponseEntity<List<WaiterLeaderboardEntry>> getLeaderboard(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(performanceService.getLeaderboard(restaurantId, startDate, endDate));
    }

    // ==================== Manual Event Recording ====================

    @PostMapping("/waiter/{waiterId}/record-complaint")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Record complaint", description = "Manually record a complaint against a waiter")
    public ResponseEntity<Map<String, Object>> recordComplaint(
            @PathVariable Long waiterId,
            @RequestParam Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        performanceService.recordComplaint(waiterId, restaurantId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Complaint recorded"));
    }

    @PostMapping("/waiter/{waiterId}/record-compliment")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Record compliment", description = "Manually record a compliment for a waiter")
    public ResponseEntity<Map<String, Object>> recordCompliment(
            @PathVariable Long waiterId,
            @RequestParam Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        performanceService.recordCompliment(waiterId, restaurantId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Compliment recorded"));
    }

    @PostMapping("/waiter/{waiterId}/record-rating")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Record rating", description = "Record a customer rating for a waiter")
    public ResponseEntity<Map<String, Object>> recordRating(
            @PathVariable Long waiterId,
            @RequestParam Long restaurantId,
            @RequestParam BigDecimal rating) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        if (rating.compareTo(BigDecimal.ONE) < 0 || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Rating must be between 1 and 5"));
        }
        performanceService.recordRating(waiterId, restaurantId, rating);
        return ResponseEntity.ok(Map.of("success", true, "message", "Rating recorded"));
    }

    @PostMapping("/waiter/{waiterId}/record-tip")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @Operation(summary = "Record tip", description = "Record a tip received by a waiter")
    public ResponseEntity<Map<String, Object>> recordTip(
            @PathVariable Long waiterId,
            @RequestParam Long restaurantId,
            @RequestParam BigDecimal amount) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        performanceService.recordTip(waiterId, restaurantId, amount);
        return ResponseEntity.ok(Map.of("success", true, "message", "Tip recorded"));
    }

    @PostMapping("/waiter/{waiterId}/shift-start")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @Operation(summary = "Record shift start", description = "Record when a waiter starts their shift")
    public ResponseEntity<Map<String, Object>> recordShiftStart(
            @PathVariable Long waiterId,
            @RequestParam Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        performanceService.recordShiftStart(waiterId, restaurantId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Shift started"));
    }

    @PostMapping("/waiter/{waiterId}/shift-end")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    @Operation(summary = "Record shift end", description = "Record when a waiter ends their shift")
    public ResponseEntity<Map<String, Object>> recordShiftEnd(
            @PathVariable Long waiterId,
            @RequestParam Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        performanceService.recordShiftEnd(waiterId, restaurantId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Shift ended"));
    }
}
