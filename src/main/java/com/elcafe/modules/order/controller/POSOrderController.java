package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.pos.CreatePOSOrderRequest;
import com.elcafe.modules.order.dto.pos.POSKitchenStatusDTO;
import com.elcafe.modules.order.dto.pos.POSOrderResponse;
import com.elcafe.modules.order.dto.pos.POSProductAvailabilityDTO;
import com.elcafe.modules.order.service.POSOrderService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/pos/orders")
@RequiredArgsConstructor
@Tag(name = "POS Orders", description = "Point of Sale system endpoints for in-store orders")
public class POSOrderController {

    private final POSOrderService posOrderService;

    @PostMapping
    @Operation(
            summary = "Create POS order",
            description = "Create a new order from POS system (Delivery, Takeaway, or Dine-in)"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> createOrder(
            @Valid @RequestBody CreatePOSOrderRequest request) {

        log.info("Received POS order request: type={}, restaurant={}, items={}",
                request.getOrderType(), request.getRestaurantId(), request.getItems().size());

        POSOrderResponse response = posOrderService.createOrder(request);

        log.info("POS order created successfully: {}", response.getOrderNumber());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", response));
    }

    @GetMapping("/products/{productId}/availability")
    @Operation(
            summary = "Check product availability",
            description = "Check if a product is available based on ingredient stock levels"
    )
    public ResponseEntity<ApiResponse<POSProductAvailabilityDTO>> checkProductAvailability(
            @PathVariable Long productId,
            @RequestParam Long restaurantId) {

        log.info("Checking availability for product: {} at restaurant: {}", productId, restaurantId);

        POSProductAvailabilityDTO availability = posOrderService.getProductAvailability(productId, restaurantId);

        return ResponseEntity.ok(ApiResponse.success("Availability checked", availability));
    }

    @GetMapping("/{orderId}/kitchen-status")
    @Operation(
            summary = "Get kitchen status",
            description = "Get the current kitchen preparation status for an order"
    )
    public ResponseEntity<ApiResponse<POSKitchenStatusDTO>> getKitchenStatus(
            @PathVariable Long orderId) {

        log.info("Getting kitchen status for order: {}", orderId);

        POSKitchenStatusDTO status = posOrderService.getKitchenStatus(orderId);

        return ResponseEntity.ok(ApiResponse.success("Kitchen status retrieved", status));
    }
}
