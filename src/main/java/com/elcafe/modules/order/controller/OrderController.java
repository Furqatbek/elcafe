package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.security.CurrentUser;
import com.elcafe.security.UserPrincipal;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class OrderController {

    private final OrderService orderService;
    private final RestaurantRepository restaurantRepository;
    private final ShiftTimeService shiftTimeService;

    @PostMapping
    @Operation(summary = "Create order", description = "Create a new order")
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @Valid @RequestBody Order order,
            @CurrentUser UserPrincipal currentUser
    ) {
        // Set restaurant if not provided in the request
        if (order.getRestaurant() == null || order.getRestaurant().getId() == null) {
            // Get the first available restaurant as default
            Restaurant restaurant = restaurantRepository.findAll().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No restaurant found in the system"));
            order.setRestaurant(restaurant);
        }

        Order createdOrder = orderService.createOrder(order);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", createdOrder));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status", description = "Update the status of an order")
    public ResponseEntity<ApiResponse<Order>> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false, defaultValue = "OPERATOR") String changedBy
    ) {
        Order order = orderService.updateOrderStatus(id, status, notes, changedBy);
        return ResponseEntity.ok(ApiResponse.success("Order status updated", order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order", description = "Get order by ID")
    public ResponseEntity<ApiResponse<Order>> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping("/number/{orderNumber}")
    @Operation(summary = "Get order by number", description = "Get order by order number")
    public ResponseEntity<ApiResponse<Order>> getOrderByNumber(@PathVariable String orderNumber) {
        Order order = orderService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping
    @Operation(summary = "List orders", description = "Get all orders with pagination and filters. All date filtering uses shift-aware logic based on restaurant business hours.")
    public ResponseEntity<ApiResponse<Page<Order>>> getAllOrders(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) OrderType orderType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shiftDate,
            @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        LocalDateTime effectiveFromDate = fromDate;
        LocalDateTime effectiveToDate = toDate;

        // If shiftDate is provided (single day shift-aware filtering)
        if (shiftDate != null && fromDate == null && toDate == null) {
            ShiftTimeService.ShiftTimeRange shiftRange = shiftTimeService.getShiftTimeRange(restaurantId, shiftDate);
            effectiveFromDate = shiftRange.start();
            effectiveToDate = shiftRange.end();
        }
        // If date range is provided, use shift-aware boundaries for both dates
        else if (fromDate != null || toDate != null) {
            LocalDate startDate = fromDate != null ? fromDate.toLocalDate() : null;
            LocalDate endDate = toDate != null ? toDate.toLocalDate() : null;

            if (startDate != null && endDate != null) {
                // Multi-day range: use shift boundaries
                ShiftTimeService.ShiftTimeRange range = shiftTimeService.getShiftTimeRangeForPeriod(restaurantId, startDate, endDate);
                effectiveFromDate = range.start();
                effectiveToDate = range.end();
            } else if (startDate != null) {
                // Only start date provided
                ShiftTimeService.ShiftTimeRange range = shiftTimeService.getShiftTimeRange(restaurantId, startDate);
                effectiveFromDate = range.start();
            } else if (endDate != null) {
                // Only end date provided
                ShiftTimeService.ShiftTimeRange range = shiftTimeService.getShiftTimeRange(restaurantId, endDate);
                effectiveToDate = range.end();
            }
        }

        // If any filter is provided, use filtered query
        if (restaurantId != null || status != null || orderType != null || effectiveFromDate != null || effectiveToDate != null || search != null) {
            Page<Order> orders = orderService.getOrdersWithFilters(
                    restaurantId, status, orderType, effectiveFromDate, effectiveToDate, search, true, pageable
            );
            return ResponseEntity.ok(ApiResponse.success(orders));
        }
        // Otherwise, use simple query
        Page<Order> orders = orderService.getAllOrders(pageable);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/restaurant/{restaurantId}")
    @Operation(summary = "Get restaurant orders", description = "Get orders for a specific restaurant")
    public ResponseEntity<ApiResponse<List<Order>>> getRestaurantOrders(@PathVariable Long restaurantId) {
        List<Order> orders = orderService.getOrdersByRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending orders", description = "Get all pending orders")
    public ResponseEntity<ApiResponse<List<Order>>> getPendingOrders() {
        List<Order> orders = orderService.getPendingOrders();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @PatchMapping("/{id}/revert")
    @Operation(summary = "Revert closed order to active", description = "Revert a DELIVERED or COMPLETED order back to an active status. This voids all payments and resets the order.")
    public ResponseEntity<ApiResponse<Order>> revertOrderToActive(
            @PathVariable Long id,
            @RequestParam(required = false) OrderStatus targetStatus,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false, defaultValue = "MANAGER") String revertedBy
    ) {
        Order order = orderService.revertOrderToActive(id, targetStatus, reason, revertedBy);
        return ResponseEntity.ok(ApiResponse.success("Order reverted to active status", order));
    }
}
