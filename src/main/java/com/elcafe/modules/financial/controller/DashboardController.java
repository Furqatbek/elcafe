package com.elcafe.modules.financial.controller;

import com.elcafe.modules.financial.dto.DashboardResponse;
import com.elcafe.modules.financial.service.DashboardService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Financial dashboard and analytics APIs")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(
            summary = "Get dashboard data",
            description = "Get comprehensive financial dashboard data for a date range"
    )
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @RequestParam Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Getting dashboard for restaurant {} from {} to {}", restaurantId, startDate, endDate);

        DashboardResponse dashboard = dashboardService.getDashboard(restaurantId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success("Dashboard data retrieved successfully", dashboard));
    }

    @GetMapping("/today")
    @Operation(
            summary = "Get today's summary",
            description = "Get quick financial summary for the current day"
    )
    public ResponseEntity<ApiResponse<DashboardResponse>> getTodaySummary(
            @RequestParam Long restaurantId) {

        log.info("Getting today's summary for restaurant {}", restaurantId);

        DashboardResponse dashboard = dashboardService.getTodaySummary(restaurantId);

        return ResponseEntity.ok(ApiResponse.success("Today's summary retrieved successfully", dashboard));
    }

    @GetMapping("/week")
    @Operation(
            summary = "Get weekly summary",
            description = "Get financial summary for the current week"
    )
    public ResponseEntity<ApiResponse<DashboardResponse>> getWeekSummary(
            @RequestParam Long restaurantId) {

        log.info("Getting weekly summary for restaurant {}", restaurantId);

        DashboardResponse dashboard = dashboardService.getWeekSummary(restaurantId);

        return ResponseEntity.ok(ApiResponse.success("Weekly summary retrieved successfully", dashboard));
    }

    @GetMapping("/month")
    @Operation(
            summary = "Get monthly summary",
            description = "Get financial summary for the current month"
    )
    public ResponseEntity<ApiResponse<DashboardResponse>> getMonthSummary(
            @RequestParam Long restaurantId) {

        log.info("Getting monthly summary for restaurant {}", restaurantId);

        DashboardResponse dashboard = dashboardService.getMonthSummary(restaurantId);

        return ResponseEntity.ok(ApiResponse.success("Monthly summary retrieved successfully", dashboard));
    }
}
