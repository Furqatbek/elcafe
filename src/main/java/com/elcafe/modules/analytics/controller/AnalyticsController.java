package com.elcafe.modules.analytics.controller;

import com.elcafe.modules.analytics.dto.*;
import com.elcafe.modules.analytics.service.*;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller for business analytics endpoints
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Business analytics and reporting APIs")
public class AnalyticsController {

    private final FinancialAnalyticsService financialAnalyticsService;
    private final OperationalAnalyticsService operationalAnalyticsService;
    private final CustomerAnalyticsService customerAnalyticsService;
    private final InventoryAnalyticsService inventoryAnalyticsService;
    private final AnalyticsSummaryService analyticsSummaryService;

    // ========== Summary Endpoint ==========

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get analytics summary", description = "Comprehensive analytics dashboard with key metrics")
    public ResponseEntity<ApiResponse<AnalyticsSummaryDTO>> getAnalyticsSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) BigDecimal laborCosts,
            @RequestParam(required = false) BigDecimal operatingExpenses
    ) {
        AnalyticsSummaryDTO summary = analyticsSummaryService.getAnalyticsSummary(
                startDate, endDate, restaurantId, laborCosts, operatingExpenses);
        return ResponseEntity.ok(ApiResponse.success("Analytics summary retrieved successfully", summary));
    }

    // ========== Financial Analytics ==========

    @GetMapping("/financial/daily-revenue")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get daily revenue", description = "Daily revenue breakdown with payment methods")
    public ResponseEntity<ApiResponse<List<DailyRevenueDTO>>> getDailyRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId
    ) {
        List<DailyRevenueDTO> revenue = financialAnalyticsService.getDailyRevenue(startDate, endDate, restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Daily revenue retrieved successfully", revenue));
    }

    @GetMapping("/financial/sales-by-category")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get sales per category", description = "Sales breakdown by product category")
    public ResponseEntity<ApiResponse<List<SalesPerCategoryDTO>>> getSalesPerCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId
    ) {
        List<SalesPerCategoryDTO> sales = financialAnalyticsService.getSalesPerCategory(startDate, endDate, restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Sales per category retrieved successfully", sales));
    }

    @GetMapping("/financial/cogs")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get COGS analytics", description = "Cost of Goods Sold and food cost percentage")
    public ResponseEntity<ApiResponse<COGSAnalyticsDTO>> getCOGSAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId
    ) {
        COGSAnalyticsDTO cogs = financialAnalyticsService.getCOGSAnalytics(startDate, endDate, restaurantId);
        return ResponseEntity.ok(ApiResponse.success("COGS analytics retrieved successfully", cogs));
    }

    @GetMapping("/financial/profitability")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get profitability analytics", description = "Comprehensive profitability including labor costs")
    public ResponseEntity<ApiResponse<ProfitabilityAnalyticsDTO>> getProfitabilityAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) BigDecimal laborCosts,
            @RequestParam(required = false) BigDecimal operatingExpenses
    ) {
        ProfitabilityAnalyticsDTO profitability = financialAnalyticsService.getProfitabilityAnalytics(
                startDate, endDate, restaurantId, laborCosts, operatingExpenses);
        return ResponseEntity.ok(ApiResponse.success("Profitability analytics retrieved successfully", profitability));
    }

    @GetMapping("/financial/contribution-margins")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get contribution margins", description = "Contribution margin per menu item")
    public ResponseEntity<ApiResponse<List<ContributionMarginDTO>>> getContributionMargins(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId
    ) {
        List<ContributionMarginDTO> margins = financialAnalyticsService.getContributionMargins(startDate, endDate, restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Contribution margins retrieved successfully", margins));
    }

    // ========== Operational Analytics ==========

    @GetMapping("/operational/sales-per-hour")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get sales per hour", description = "Hourly sales breakdown")
    public ResponseEntity<ApiResponse<List<SalesPerHourDTO>>> getSalesPerHour(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId
    ) {
        List<SalesPerHourDTO> sales = operationalAnalyticsService.getSalesPerHour(startDate, endDate, restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Sales per hour retrieved successfully", sales));
    }

    @GetMapping("/operational/peak-hours")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get peak hours", description = "Identify peak business hours")
    public ResponseEntity<ApiResponse<PeakHoursDTO>> getPeakHours(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId
    ) {
        PeakHoursDTO peakHours = operationalAnalyticsService.getPeakHours(startDate, endDate, restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Peak hours retrieved successfully", peakHours));
    }

    @GetMapping("/operational/table-turnover")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get table turnover", description = "Table turnover rate and occupancy")
    public ResponseEntity<ApiResponse<TableTurnoverDTO>> getTableTurnover(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) Integer totalTables,
            @RequestParam(required = false) Integer totalSeats,
            @RequestParam(required = false) Integer operatingHoursPerDay
    ) {
        TableTurnoverDTO turnover = operationalAnalyticsService.getTableTurnover(
                startDate, endDate, restaurantId, totalTables, totalSeats, operatingHoursPerDay);
        return ResponseEntity.ok(ApiResponse.success("Table turnover retrieved successfully", turnover));
    }

    @GetMapping("/operational/order-timing")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get order timing analytics", description = "Preparation, wait time, and delivery metrics")
    public ResponseEntity<ApiResponse<OrderTimingAnalyticsDTO>> getOrderTimingAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId
    ) {
        OrderTimingAnalyticsDTO timing = operationalAnalyticsService.getOrderTimingAnalytics(startDate, endDate, restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Order timing analytics retrieved successfully", timing));
    }

    @GetMapping("/operational/kitchen")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'KITCHEN_STAFF')")
    @Operation(summary = "Get kitchen analytics", description = "Kitchen performance, preparation times, and chef metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKitchenAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId
    ) {
        Map<String, Object> analytics = operationalAnalyticsService.getKitchenAnalytics(startDate, endDate, restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Kitchen analytics retrieved successfully", analytics));
    }

    // ========== Customer Analytics ==========

    @GetMapping("/customer/retention")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get customer retention", description = "Customer retention and churn rate")
    public ResponseEntity<ApiResponse<CustomerRetentionDTO>> getCustomerRetention(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId
    ) {
        CustomerRetentionDTO retention = customerAnalyticsService.getCustomerRetention(startDate, endDate, restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Customer retention retrieved successfully", retention));
    }

    @GetMapping("/customer/ltv")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get customer lifetime value", description = "Customer LTV and related metrics")
    public ResponseEntity<ApiResponse<CustomerLTVDTO>> getCustomerLTV(
            @RequestParam(required = false) Long restaurantId
    ) {
        CustomerLTVDTO ltv = customerAnalyticsService.getCustomerLTV(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Customer LTV retrieved successfully", ltv));
    }

    @GetMapping("/customer/satisfaction")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get customer satisfaction", description = "Aggregated satisfaction scores from all sources")
    public ResponseEntity<ApiResponse<CustomerSatisfactionDTO>> getCustomerSatisfaction(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        CustomerSatisfactionDTO satisfaction = customerAnalyticsService.getCustomerSatisfaction(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Customer satisfaction retrieved successfully", satisfaction));
    }

    // ========== Inventory Analytics ==========

    @GetMapping("/inventory/turnover")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get inventory turnover", description = "Inventory turnover ratio and ingredient-level metrics")
    public ResponseEntity<ApiResponse<InventoryTurnoverDTO>> getInventoryTurnover(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long restaurantId
    ) {
        InventoryTurnoverDTO turnover = inventoryAnalyticsService.getInventoryTurnover(startDate, endDate, restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Inventory turnover retrieved successfully", turnover));
    }
}
