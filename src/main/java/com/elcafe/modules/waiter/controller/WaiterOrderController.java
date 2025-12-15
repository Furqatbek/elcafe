package com.elcafe.modules.waiter.controller;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.waiter.dto.AddOrderItemRequest;
import com.elcafe.modules.waiter.dto.CreateOrderRequest;
import com.elcafe.modules.waiter.dto.OrderEventResponse;
import com.elcafe.modules.waiter.dto.UpdateOrderItemRequest;
import com.elcafe.modules.waiter.service.OrderEventService;
import com.elcafe.modules.waiter.service.WaiterOrderService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for waiter order operations
 */
@RestController
@RequestMapping("/api/v1/waiter/orders")
@RequiredArgsConstructor
@Tag(name = "Waiter Orders", description = "Order management endpoints for waiters")
@SecurityRequirement(name = "bearerAuth")
public class WaiterOrderController {

    private final WaiterOrderService waiterOrderService;
    private final OrderEventService orderEventService;

    @PostMapping
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR')")
    @Operation(summary = "Create order", description = "Create a new order for a table")
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        Order order = waiterOrderService.createOrder(request, waiterId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", order));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR', 'ADMIN', 'OPERATOR')")
    @Operation(summary = "Get order", description = "Get order details by ID")
    public ResponseEntity<ApiResponse<Order>> getOrder(@PathVariable Long id) {
        Order order = waiterOrderService.getOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved successfully", order));
    }

    @GetMapping("/table/{tableId}")
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR', 'ADMIN', 'OPERATOR')")
    @Operation(summary = "Get table orders", description = "Get all active orders for a table")
    public ResponseEntity<ApiResponse<List<Order>>> getTableOrders(@PathVariable Long tableId) {
        List<Order> orders = waiterOrderService.getTableOrders(tableId);
        return ResponseEntity.ok(ApiResponse.success("Table orders retrieved successfully", orders));
    }

    @PostMapping("/{orderId}/items")
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR')")
    @Operation(summary = "Add items to order", description = "Add one or more items to an existing order")
    public ResponseEntity<ApiResponse<Order>> addItems(
            @PathVariable Long orderId,
            @Valid @RequestBody List<AddOrderItemRequest> items,
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        Order order = waiterOrderService.addItems(orderId, items, waiterId);
        return ResponseEntity.ok(ApiResponse.success("Items added to order successfully", order));
    }

    @PutMapping("/{orderId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR')")
    @Operation(summary = "Update order item", description = "Update an existing order item")
    public ResponseEntity<ApiResponse<Order>> updateItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateOrderItemRequest request,
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        Order order = waiterOrderService.updateItem(orderId, itemId, request, waiterId);
        return ResponseEntity.ok(ApiResponse.success("Order item updated successfully", order));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR')")
    @Operation(summary = "Remove order item", description = "Remove an item from order")
    public ResponseEntity<ApiResponse<Order>> removeItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        Order order = waiterOrderService.removeItem(orderId, itemId, waiterId);
        return ResponseEntity.ok(ApiResponse.success("Order item removed successfully", order));
    }

    @PostMapping("/{orderId}/submit")
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR')")
    @Operation(summary = "Submit order to kitchen", description = "Submit order to kitchen for preparation")
    public ResponseEntity<ApiResponse<Order>> submitToKitchen(
            @PathVariable Long orderId,
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        Order order = waiterOrderService.submitToKitchen(orderId, waiterId);
        return ResponseEntity.ok(ApiResponse.success("Order submitted to kitchen successfully", order));
    }

    @PostMapping("/{orderId}/items/{itemId}/deliver")
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR')")
    @Operation(summary = "Mark item as delivered", description = "Mark an order item as delivered to customer")
    public ResponseEntity<ApiResponse<Order>> markItemDelivered(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        Order order = waiterOrderService.markItemDelivered(orderId, itemId, waiterId);
        return ResponseEntity.ok(ApiResponse.success("Item marked as delivered successfully", order));
    }

    @PostMapping("/{orderId}/bill")
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR')")
    @Operation(summary = "Request bill", description = "Request bill for the order")
    public ResponseEntity<ApiResponse<Order>> requestBill(
            @PathVariable Long orderId,
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        Order order = waiterOrderService.requestBill(orderId, waiterId);
        return ResponseEntity.ok(ApiResponse.success("Bill requested successfully", order));
    }

    @PostMapping("/{orderId}/close")
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR')")
    @Operation(summary = "Close order", description = "Close order after payment completion")
    public ResponseEntity<ApiResponse<Order>> closeOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        Order order = waiterOrderService.closeOrder(orderId, waiterId);
        return ResponseEntity.ok(ApiResponse.success("Order closed successfully", order));
    }

    @GetMapping("/{orderId}/history")
    @PreAuthorize("hasAnyRole('WAITER', 'SUPERVISOR', 'ADMIN', 'OPERATOR')")
    @Operation(summary = "Get order history", description = "Get complete event history for an order")
    public ResponseEntity<ApiResponse<List<OrderEventResponse>>> getOrderHistory(
            @PathVariable Long orderId) {
        List<OrderEventResponse> events = orderEventService.getOrderHistory(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order history retrieved successfully", events));
    }
}
