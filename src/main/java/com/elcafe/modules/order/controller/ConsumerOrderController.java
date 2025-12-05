package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.consumer.CreateOrderRequest;
import com.elcafe.modules.order.dto.consumer.OrderResponse;
import com.elcafe.modules.order.service.ConsumerOrderService;
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
@RequestMapping("/api/v1/consumer/orders")
@RequiredArgsConstructor
@Tag(name = "Consumer Orders", description = "Public API for placing orders from website/mobile app")
public class ConsumerOrderController {

    private final ConsumerOrderService consumerOrderService;

    @PostMapping
    @Operation(summary = "Place order", description = "Place a new food order (public API for website/mobile)")
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("Received order request from {} for restaurant {}",
                request.getOrderSource(), request.getRestaurantId());

        OrderResponse response = consumerOrderService.placeOrder(request);

        log.info("Order created successfully: {}", response.getOrderNumber());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", response));
    }

    @GetMapping("/{orderNumber}")
    @Operation(summary = "Track order", description = "Get order status and details by order number")
    public ResponseEntity<ApiResponse<OrderResponse>> trackOrder(@PathVariable String orderNumber) {
        OrderResponse response = consumerOrderService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved successfully", response));
    }

    @PostMapping("/{orderNumber}/cancel")
    @Operation(summary = "Cancel order", description = "Cancel an order (only if not yet preparing)")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable String orderNumber,
            @RequestParam(required = false) String reason) {
        OrderResponse response = consumerOrderService.cancelOrder(orderNumber, reason);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", response));
    }
}
