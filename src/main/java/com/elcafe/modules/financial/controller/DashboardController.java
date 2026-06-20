package com.elcafe.modules.financial.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.financial.dto.DashboardResponse;
import com.elcafe.modules.financial.service.DashboardService;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Financial dashboard and analytics APIs")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
public class DashboardController {

    private final RestaurantAuthorizationService restaurantAuthorizationService;
    private final DashboardService dashboardService;
    private final ShiftTimeService shiftTimeService;

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
        restaurantAuthorizationService.checkAccess(restaurantId);

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
        restaurantAuthorizationService.checkAccess(restaurantId);

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
        restaurantAuthorizationService.checkAccess(restaurantId);

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
        restaurantAuthorizationService.checkAccess(restaurantId);

        DashboardResponse dashboard = dashboardService.getMonthSummary(restaurantId);

        return ResponseEntity.ok(ApiResponse.success("Monthly summary retrieved successfully", dashboard));
    }

    @GetMapping("/current-business-day")
    @Operation(
            summary = "Get current business day",
            description = "Get the current business day based on restaurant's business hours. " +
                    "For shifts that cross midnight (e.g., 21:00-03:00), if current time is before " +
                    "opening time, returns yesterday's date as the current business day."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentBusinessDay(
            @RequestParam Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);

        LocalDate businessDay = shiftTimeService.getCurrentBusinessDay(restaurantId);
        LocalDate calendarDay = LocalDate.now();
        ShiftTimeService.ShiftTimeRange shiftRange = shiftTimeService.getShiftTimeRange(restaurantId, businessDay);

        Map<String, Object> response = Map.of(
                "currentBusinessDay", businessDay,
                "calendarDay", calendarDay,
                "shiftStart", shiftRange.start(),
                "shiftEnd", shiftRange.end(),
                "openTime", shiftRange.openTime(),
                "closeTime", shiftRange.closeTime()
        );

        log.info("Current business day for restaurant {}: {} (calendar: {})", restaurantId, businessDay, calendarDay);

        return ResponseEntity.ok(ApiResponse.success("Current business day retrieved", response));
    }
}
