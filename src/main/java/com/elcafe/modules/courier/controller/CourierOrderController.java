package com.elcafe.modules.courier.controller;

import com.elcafe.modules.courier.dto.CourierLocationResponse;
import com.elcafe.modules.courier.dto.CourierLocationUpdateRequest;
import com.elcafe.modules.courier.service.CourierLocationService;
import com.elcafe.modules.courier.service.CourierOrderService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courier/orders")
@RequiredArgsConstructor
@Tag(name = "Courier Orders", description = "Order management for couriers")
@SecurityRequirement(name = "Bearer Authentication")
public class CourierOrderController {

    private final CourierOrderService courierOrderService;
    private final CourierLocationService courierLocationService;

    @GetMapping("/available")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "Get available orders", description = "Get orders ready for courier assignment")
    public ResponseEntity<ApiResponse<List<Order>>> getAvailableOrders(
            @RequestParam(required = false) Long restaurantId) {
        List<Order> orders = courierOrderService.getAvailableOrders(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Available orders retrieved", orders));
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "Get my orders", description = "Get orders assigned to current courier")
    public ResponseEntity<ApiResponse<List<Order>>> getMyOrders(@RequestParam Long courierId) {
        List<Order> orders = courierOrderService.getCourierOrders(courierId);
        return ResponseEntity.ok(ApiResponse.success("Your orders retrieved", orders));
    }

    @PostMapping("/{orderId}/accept")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "Accept order", description = "Courier accepts an order for delivery")
    public ResponseEntity<ApiResponse<Order>> acceptOrder(
            @PathVariable Long orderId,
            @RequestParam Long courierId) {
        Order order = courierOrderService.acceptOrder(orderId, courierId);
        return ResponseEntity.ok(ApiResponse.success("Order accepted", order));
    }

    @PostMapping("/{orderId}/decline")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "Decline order", description = "Courier declines an order")
    public ResponseEntity<ApiResponse<String>> declineOrder(
            @PathVariable Long orderId,
            @RequestParam Long courierId,
            @RequestParam(required = false) String reason) {
        courierOrderService.declineOrder(orderId, courierId, reason);
        return ResponseEntity.ok(ApiResponse.success("Order declined", null));
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Assign courier", description = "Manually assign a courier to an order")
    public ResponseEntity<ApiResponse<Order>> assignCourier(
            @RequestParam Long orderId,
            @RequestParam Long courierId) {
        Order order = courierOrderService.assignCourier(orderId, courierId);
        return ResponseEntity.ok(ApiResponse.success("Courier assigned", order));
    }

    @PostMapping("/{orderId}/start-delivery")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "Start delivery", description = "Mark order as out for delivery")
    public ResponseEntity<ApiResponse<Order>> startDelivery(
            @PathVariable Long orderId,
            @RequestParam Long courierId) {
        Order order = courierOrderService.startDelivery(orderId, courierId);
        return ResponseEntity.ok(ApiResponse.success("Delivery started", order));
    }

    @PostMapping("/{orderId}/complete")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "Complete delivery", description = "Mark order as delivered")
    public ResponseEntity<ApiResponse<Order>> completeDelivery(
            @PathVariable Long orderId,
            @RequestParam Long courierId,
            @RequestParam(required = false) String notes) {
        Order order = courierOrderService.completeDelivery(orderId, courierId, notes);
        return ResponseEntity.ok(ApiResponse.success("Order delivered", order));
    }

    // ========== Location Tracking ==========

    @PostMapping("/location")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "Update courier location", description = "Update courier's GPS location for real-time tracking")
    public ResponseEntity<ApiResponse<CourierLocationResponse>> updateLocation(
            @RequestParam Long courierId,
            @Valid @RequestBody CourierLocationUpdateRequest request) {
        CourierLocationResponse location = courierLocationService.updateLocation(courierId, request);
        return ResponseEntity.ok(ApiResponse.success("Location updated", location));
    }

    @GetMapping("/location/{courierId}")
    @PreAuthorize("hasAnyRole('COURIER', 'ADMIN', 'OPERATOR')")
    @Operation(summary = "Get courier location", description = "Get latest location of a courier")
    public ResponseEntity<ApiResponse<CourierLocationResponse>> getCourierLocation(
            @PathVariable Long courierId) {
        CourierLocationResponse location = courierLocationService.getLatestLocation(courierId);
        return ResponseEntity.ok(ApiResponse.success("Courier location retrieved", location));
    }

    @GetMapping("/{orderId}/location")
    @PreAuthorize("hasAnyRole('COURIER', 'ADMIN', 'OPERATOR')")
    @Operation(summary = "Get order location", description = "Get current location of courier delivering this order")
    public ResponseEntity<ApiResponse<CourierLocationResponse>> getOrderLocation(
            @PathVariable Long orderId) {
        CourierLocationResponse location = courierLocationService.getOrderLocation(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order location retrieved", location));
    }

    @GetMapping("/{orderId}/route")
    @PreAuthorize("hasAnyRole('COURIER', 'ADMIN', 'OPERATOR')")
    @Operation(summary = "Get order delivery route", description = "Get full delivery route for an order")
    public ResponseEntity<ApiResponse<List<CourierLocationResponse>>> getOrderRoute(
            @PathVariable Long orderId) {
        List<CourierLocationResponse> route = courierLocationService.getOrderRoute(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order route retrieved", route));
    }

    @GetMapping("/location/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get active courier locations", description = "Get locations of all active couriers")
    public ResponseEntity<ApiResponse<List<CourierLocationResponse>>> getActiveCourierLocations() {
        List<CourierLocationResponse> locations = courierLocationService.getActiveCourierLocations();
        return ResponseEntity.ok(ApiResponse.success("Active courier locations retrieved", locations));
    }
}
