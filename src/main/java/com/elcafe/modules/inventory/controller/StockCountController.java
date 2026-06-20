package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.inventory.dto.StockCountRequest;
import com.elcafe.modules.inventory.dto.StockCountResponse;
import com.elcafe.modules.inventory.dto.VarianceReportResponse;
import com.elcafe.modules.inventory.entity.StockCount;
import com.elcafe.modules.inventory.entity.StockCountItem;
import com.elcafe.modules.inventory.service.StockCountService;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory/stock-counts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'OPERATOR')")
public class StockCountController {

    private final StockCountService stockCountService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockCountResponse>>> getStockCounts(
            @RequestParam Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        log.info("Fetching stock counts for restaurant: {}", restaurantId);

        List<StockCount> stockCounts = stockCountService.getStockCountsByRestaurant(restaurantId);
        List<StockCountResponse> responses = stockCounts.stream()
                .map(StockCountResponse::fromEntityWithoutItems)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Stock counts retrieved successfully", responses));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<StockCountResponse>>> getActiveStockCounts(
            @RequestParam Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        log.info("Fetching active stock counts for restaurant: {}", restaurantId);

        List<StockCount> stockCounts = stockCountService.getActiveStockCounts(restaurantId);
        List<StockCountResponse> responses = stockCounts.stream()
                .map(StockCountResponse::fromEntityWithoutItems)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Active stock counts retrieved successfully", responses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockCountResponse>> getStockCountById(@PathVariable Long id) {
        log.info("Fetching stock count: {}", id);

        StockCount stockCount = stockCountService.getStockCountById(id);
        StockCountResponse response = StockCountResponse.fromEntity(stockCount);

        return ResponseEntity.ok(ApiResponse.success("Stock count retrieved successfully", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StockCountResponse>> createStockCount(
            @Valid @RequestBody StockCountRequest request) {
        restaurantAuthorizationService.checkAccess(request.getRestaurantId());
        log.info("Creating stock count for restaurant: {}", request.getRestaurantId());

        StockCount stockCount = stockCountService.createStockCount(request);
        StockCountResponse response = StockCountResponse.fromEntity(stockCount);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock count created successfully", response));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<StockCountResponse>> startStockCount(
            @PathVariable Long id,
            @RequestParam String countedBy) {
        log.info("Starting stock count: {}", id);

        StockCount stockCount = stockCountService.startStockCount(id, countedBy);
        StockCountResponse response = StockCountResponse.fromEntity(stockCount);

        return ResponseEntity.ok(ApiResponse.success("Stock count started successfully", response));
    }

    @PostMapping("/items/record-count")
    public ResponseEntity<ApiResponse<StockCountResponse.StockCountItemResponse>> recordCount(
            @Valid @RequestBody StockCountRequest.RecordCountRequest request) {
        log.info("Recording count for item: {}", request.getItemId());

        StockCountItem item = stockCountService.recordCount(request);
        StockCountResponse.StockCountItemResponse response = StockCountResponse.StockCountItemResponse.fromEntity(item);

        return ResponseEntity.ok(ApiResponse.success("Count recorded successfully", response));
    }

    @PostMapping("/items/variance-reason")
    public ResponseEntity<ApiResponse<StockCountResponse.StockCountItemResponse>> setVarianceReason(
            @Valid @RequestBody StockCountRequest.VarianceReasonRequest request) {
        log.info("Setting variance reason for item: {}", request.getItemId());

        StockCountItem item = stockCountService.setVarianceReason(request);
        StockCountResponse.StockCountItemResponse response = StockCountResponse.StockCountItemResponse.fromEntity(item);

        return ResponseEntity.ok(ApiResponse.success("Variance reason set successfully", response));
    }

    @PostMapping("/{id}/submit-review")
    public ResponseEntity<ApiResponse<StockCountResponse>> submitForReview(
            @PathVariable Long id,
            @RequestParam String reviewedBy) {
        log.info("Submitting stock count for review: {}", id);

        StockCount stockCount = stockCountService.submitForReview(id, reviewedBy);
        StockCountResponse response = StockCountResponse.fromEntity(stockCount);

        return ResponseEntity.ok(ApiResponse.success("Stock count submitted for review successfully", response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockCountResponse>> approveStockCount(
            @PathVariable Long id,
            @Valid @RequestBody StockCountRequest.ApproveRequest request) {
        log.info("Approving stock count: {}", id);

        StockCount stockCount = stockCountService.approveStockCount(id, request);
        StockCountResponse response = StockCountResponse.fromEntity(stockCount);

        return ResponseEntity.ok(ApiResponse.success("Stock count approved successfully", response));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<StockCountResponse>> cancelStockCount(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestParam String cancelledBy) {
        log.info("Cancelling stock count: {}", id);

        StockCount stockCount = stockCountService.cancelStockCount(id, reason, cancelledBy);
        StockCountResponse response = StockCountResponse.fromEntity(stockCount);

        return ResponseEntity.ok(ApiResponse.success("Stock count cancelled successfully", response));
    }

    @GetMapping("/{id}/variances")
    public ResponseEntity<ApiResponse<List<StockCountResponse.StockCountItemResponse>>> getItemsWithVariance(
            @PathVariable Long id) {
        log.info("Fetching items with variance for stock count: {}", id);

        List<StockCountItem> items = stockCountService.getItemsWithVariance(id);
        List<StockCountResponse.StockCountItemResponse> responses = items.stream()
                .map(StockCountResponse.StockCountItemResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Items with variance retrieved successfully", responses));
    }

    @GetMapping("/variance-report")
    public ResponseEntity<ApiResponse<VarianceReportResponse>> getVarianceReport(
            @RequestParam Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        log.info("Generating variance report for restaurant: {} from {} to {}", restaurantId, startDate, endDate);

        VarianceReportResponse report = stockCountService.generateVarianceReport(restaurantId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success("Variance report generated successfully", report));
    }
}
