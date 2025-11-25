package com.elcafe.modules.kitchen.controller;

import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.enums.KitchenPriority;
import com.elcafe.modules.kitchen.service.KitchenOrderService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kitchen/orders")
@RequiredArgsConstructor
@Tag(name = "Kitchen Orders", description = "Kitchen order management for food preparation")
@SecurityRequirement(name = "Bearer Authentication")
public class KitchenOrderController {

    private final KitchenOrderService kitchenOrderService;

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'KITCHEN_STAFF')")
    @Operation(summary = "Get active orders", description = "Get all pending and preparing orders")
    public ResponseEntity<ApiResponse<List<KitchenOrder>>> getActiveOrders(
            @RequestParam(required = false) Long restaurantId) {
        List<KitchenOrder> orders = kitchenOrderService.getActiveOrders(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Active orders retrieved", orders));
    }

    @GetMapping("/ready")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'KITCHEN_STAFF', 'COURIER')")
    @Operation(summary = "Get ready orders", description = "Get all orders ready for pickup")
    public ResponseEntity<ApiResponse<List<KitchenOrder>>> getReadyOrders(
            @RequestParam(required = false) Long restaurantId) {
        List<KitchenOrder> orders = kitchenOrderService.getReadyOrders(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Ready orders retrieved", orders));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'KITCHEN_STAFF')")
    @Operation(summary = "Start preparation", description = "Start preparing an order")
    public ResponseEntity<ApiResponse<KitchenOrder>> startPreparation(
            @PathVariable Long id,
            @RequestParam String chefName) {
        KitchenOrder order = kitchenOrderService.startPreparation(id, chefName);
        return ResponseEntity.ok(ApiResponse.success("Preparation started", order));
    }

    @PostMapping("/{id}/ready")
    @PreAuthorize("hasAnyRole('ADMIN', 'KITCHEN_STAFF')")
    @Operation(summary = "Mark as ready", description = "Mark order as ready for pickup/delivery")
    public ResponseEntity<ApiResponse<KitchenOrder>> markAsReady(@PathVariable Long id) {
        KitchenOrder order = kitchenOrderService.markAsReady(id);
        return ResponseEntity.ok(ApiResponse.success("Order marked as ready", order));
    }

    @PostMapping("/{id}/picked-up")
    @PreAuthorize("hasAnyRole('ADMIN', 'KITCHEN_STAFF', 'COURIER')")
    @Operation(summary = "Mark as picked up", description = "Mark order as picked up by courier")
    public ResponseEntity<ApiResponse<KitchenOrder>> markAsPickedUp(@PathVariable Long id) {
        KitchenOrder order = kitchenOrderService.markAsPickedUp(id);
        return ResponseEntity.ok(ApiResponse.success("Order marked as picked up", order));
    }

    @PatchMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Update priority", description = "Update order priority")
    public ResponseEntity<ApiResponse<KitchenOrder>> updatePriority(
            @PathVariable Long id,
            @RequestParam KitchenPriority priority) {
        KitchenOrder order = kitchenOrderService.updatePriority(id, priority);
        return ResponseEntity.ok(ApiResponse.success("Priority updated", order));
    }
}
