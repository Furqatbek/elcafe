package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.security.CurrentUser;
import com.elcafe.security.UserPrincipal;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin endpoints for order management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Orders", description = "Admin order management endpoints")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "Get all orders", description = "Get all orders with optional status filter")
    public ResponseEntity<ApiResponse<List<Order>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status
    ) {
        log.info("Admin fetching orders. Status filter: {}", status);

        List<Order> orders;
        if (status != null) {
            if (status == OrderStatus.NEW) {
                orders = orderService.getPendingOrders();
            } else {
                // TODO: Add repository method for filtering by status
                orders = orderService.getPendingOrders();
            }
        } else {
            orders = orderService.getAllOrders(org.springframework.data.domain.Pageable.unpaged()).getContent();
        }

        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully", orders));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID", description = "Get detailed order information")
    public ResponseEntity<ApiResponse<Order>> getOrder(@PathVariable Long orderId) {
        log.info("Admin fetching order: {}", orderId);
        Order order = orderService.getOrderById(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved successfully", order));
    }

    @PatchMapping("/{orderId}/status")
    @Operation(summary = "Update order status", description = "Update order status with state machine validation")
    public ResponseEntity<ApiResponse<Order>> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateStatusRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        log.info("Admin updating order {} status to: {}", orderId, request.getStatus());

        Order order = orderService.updateOrderStatus(
                orderId,
                request.getStatus(),
                request.getNotes(),
                currentUser.getEmail()
        );

        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", order));
    }

    @PostMapping("/{orderId}/accept")
    @Operation(summary = "Accept order", description = "Accept a placed order")
    public ResponseEntity<ApiResponse<Order>> acceptOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody AcceptOrderRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        log.info("Admin accepting order: {}", orderId);

        Order order = orderService.acceptOrder(
                orderId,
                currentUser.getEmail(),
                request.getNotes()
        );

        // TODO: Send WebSocket event to customer
        // TODO: Send SMS notification to customer

        return ResponseEntity.ok(ApiResponse.success("Order accepted successfully", order));
    }

    @PostMapping("/{orderId}/reject")
    @Operation(summary = "Reject order", description = "Reject a placed order and initiate refund")
    public ResponseEntity<ApiResponse<Order>> rejectOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody RejectOrderRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        log.info("Admin rejecting order: {}", orderId);

        Order order = orderService.rejectOrder(
                orderId,
                request.getReason(),
                currentUser.getEmail()
        );

        // TODO: Send WebSocket event to customer
        // TODO: Send SMS notification to customer

        return ResponseEntity.ok(ApiResponse.success("Order rejected and refund initiated", order));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel order (Admin)", description = "Cancel order by admin")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody CancelOrderRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        log.info("Admin cancelling order: {}", orderId);

        Order order = orderService.cancelOrder(
                orderId,
                request.getReason(),
                "ADMIN"
        );

        // TODO: Send WebSocket event to customer
        // TODO: Send SMS notification to customer

        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", order));
    }

    // DTOs for requests

    @Data
    public static class UpdateStatusRequest {
        @NotNull(message = "Status is required")
        private OrderStatus status;

        private String notes;
    }

    @Data
    public static class AcceptOrderRequest {
        private String notes;
    }

    @Data
    public static class RejectOrderRequest {
        @NotBlank(message = "Reason is required")
        private String reason;
    }

    @Data
    public static class CancelOrderRequest {
        @NotBlank(message = "Reason is required")
        private String reason;
    }
}
