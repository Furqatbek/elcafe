package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.OrderTrackingResponse;
import com.elcafe.modules.order.service.OrderTrackingService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/orders")
@RequiredArgsConstructor
@Tag(name = "Public Order Tracking", description = "Public endpoints for customers to track their orders")
public class PublicOrderController {

    private final OrderTrackingService orderTrackingService;

    // Tracking requires the order's unguessable token (audit #17): the order number is enumerable, so it
    // cannot authorize access on its own. The customer receives the token when the order is placed.
    @GetMapping("/{orderNumber}/status")
    @Operation(summary = "Get order status", description = "Current status + tracking; requires the order tracking token")
    public ResponseEntity<ApiResponse<OrderTrackingResponse>> getOrderStatus(
            @PathVariable String orderNumber,
            @RequestParam String token) {
        OrderTrackingResponse tracking = orderTrackingService.getOrderTracking(orderNumber, token);
        return ResponseEntity.ok(ApiResponse.success(tracking));
    }

    @GetMapping("/{orderNumber}/eta")
    @Operation(summary = "Get order ETA", description = "ETA; requires the order tracking token")
    public ResponseEntity<ApiResponse<OrderTrackingResponse.ETAInfo>> getOrderETA(
            @PathVariable String orderNumber,
            @RequestParam String token) {
        OrderTrackingResponse.ETAInfo eta = orderTrackingService.calculateETA(orderNumber, token);
        return ResponseEntity.ok(ApiResponse.success(eta));
    }

    // NOTE: the /track?phone= endpoint was removed (audit #18) — it returned any phone's recent orders
    // (delivery address, courier phone/GPS) cross-tenant with no ownership proof, and had no caller.
}
