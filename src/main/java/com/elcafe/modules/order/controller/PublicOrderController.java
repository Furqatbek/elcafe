package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.OrderTrackingResponse;
import com.elcafe.modules.order.service.OrderTrackingService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/orders")
@RequiredArgsConstructor
@Tag(name = "Public Order Tracking", description = "Public endpoints for customers to track their orders")
public class PublicOrderController {

    private final OrderTrackingService orderTrackingService;

    @GetMapping("/{orderNumber}/status")
    @Operation(summary = "Get order status", description = "Get current order status and tracking information")
    public ResponseEntity<ApiResponse<OrderTrackingResponse>> getOrderStatus(
            @PathVariable String orderNumber) {
        OrderTrackingResponse tracking = orderTrackingService.getOrderTracking(orderNumber);
        return ResponseEntity.ok(ApiResponse.success(tracking));
    }

    @GetMapping("/{orderNumber}/eta")
    @Operation(summary = "Get order ETA", description = "Get estimated time of arrival/completion")
    public ResponseEntity<ApiResponse<OrderTrackingResponse.ETAInfo>> getOrderETA(
            @PathVariable String orderNumber) {
        OrderTrackingResponse.ETAInfo eta = orderTrackingService.calculateETA(orderNumber);
        return ResponseEntity.ok(ApiResponse.success(eta));
    }

    @GetMapping("/track")
    @Operation(summary = "Track order by phone", description = "Get recent orders for a phone number")
    public ResponseEntity<ApiResponse<List<OrderTrackingResponse>>> trackByPhone(
            @RequestParam String phone) {
        List<OrderTrackingResponse> orders = orderTrackingService.getRecentOrdersByPhone(phone);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
}
