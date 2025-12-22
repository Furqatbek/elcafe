package com.elcafe.modules.order.controller;

import com.elcafe.modules.order.dto.pos.CreatePOSOrderRequest;
import com.elcafe.modules.order.dto.pos.ModifyOrderItemRequest;
import com.elcafe.modules.order.dto.pos.PaymentRequestDTO;
import com.elcafe.modules.order.dto.pos.PaymentResponseDTO;
import com.elcafe.modules.order.dto.pos.POSKitchenStatusDTO;
import com.elcafe.modules.order.dto.pos.POSOrderResponse;
import com.elcafe.modules.order.dto.pos.POSProductAvailabilityDTO;
import com.elcafe.modules.order.dto.pos.RefundRequestDTO;
import com.elcafe.modules.order.dto.pos.SplitBillDTO;
import com.elcafe.modules.order.service.PaymentService;
import com.elcafe.modules.order.service.POSOrderService;

import java.util.List;
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
    private final PaymentService paymentService;

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

    // ============== Order Management Endpoints ==============

    @GetMapping("/open/{restaurantId}")
    @Operation(
            summary = "Get open dine-in orders",
            description = "Get all open dine-in orders for a restaurant (orders with tables assigned)"
    )
    public ResponseEntity<ApiResponse<List<POSOrderResponse>>> getOpenDineInOrders(
            @PathVariable Long restaurantId) {

        log.info("Getting open dine-in orders for restaurant: {}", restaurantId);

        List<POSOrderResponse> orders = posOrderService.getOpenDineInOrders(restaurantId);

        return ResponseEntity.ok(ApiResponse.success("Open orders retrieved", orders));
    }

    @GetMapping("/{orderId}")
    @Operation(
            summary = "Get order by ID",
            description = "Get a specific order by its ID"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> getOrderById(
            @PathVariable Long orderId) {

        log.info("Getting order by ID: {}", orderId);

        POSOrderResponse order = posOrderService.getOrderById(orderId);

        return ResponseEntity.ok(ApiResponse.success("Order retrieved", order));
    }

    @PostMapping("/{orderId}/items")
    @Operation(
            summary = "Add item to order",
            description = "Add a new item to an existing order"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> addItemToOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody ModifyOrderItemRequest request) {

        log.info("Adding item to order {}: productId={}, quantity={}",
                orderId, request.getProductId(), request.getQuantity());

        POSOrderResponse order = posOrderService.addItemToOrder(orderId, request);

        return ResponseEntity.ok(ApiResponse.success("Item added to order", order));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    @Operation(
            summary = "Remove item from order",
            description = "Remove an item from an existing order"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> removeItemFromOrder(
            @PathVariable Long orderId,
            @PathVariable Long itemId) {

        log.info("Removing item {} from order {}", itemId, orderId);

        POSOrderResponse order = posOrderService.removeItemFromOrder(orderId, itemId);

        return ResponseEntity.ok(ApiResponse.success("Item removed from order", order));
    }

    @PatchMapping("/{orderId}/items/{itemId}/quantity")
    @Operation(
            summary = "Update item quantity",
            description = "Update the quantity of an item in an existing order"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> updateItemQuantity(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestParam Integer quantity) {

        log.info("Updating quantity for item {} in order {} to {}", itemId, orderId, quantity);

        POSOrderResponse order = posOrderService.updateItemQuantity(orderId, itemId, quantity);

        return ResponseEntity.ok(ApiResponse.success("Item quantity updated", order));
    }

    // ============== Split Bill Endpoints ==============

    @PostMapping("/{orderId}/split")
    @Operation(
            summary = "Split bill",
            description = "Split the bill for an order - by items, evenly, or by custom amounts"
    )
    public ResponseEntity<ApiResponse<SplitBillDTO.SplitBillResponse>> splitBill(
            @PathVariable Long orderId,
            @Valid @RequestBody SplitBillDTO request) {

        log.info("Splitting bill for order {}: mode={}", orderId, request.getMode());

        SplitBillDTO.SplitBillResponse response = posOrderService.splitBill(orderId, request);

        return ResponseEntity.ok(ApiResponse.success("Bill split successfully", response));
    }

    // ============== Payment Endpoints ==============

    @PostMapping("/{orderId}/payments")
    @Operation(
            summary = "Process payment",
            description = "Process a payment for an order (supports split payments)"
    )
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> processPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequestDTO request) {

        log.info("Processing payment for order {}: method={}, amount={}",
                orderId, request.getMethod(), request.getAmount());

        PaymentResponseDTO response = paymentService.processPOSPayment(orderId, request);

        return ResponseEntity.ok(ApiResponse.success("Payment processed successfully", response));
    }

    @GetMapping("/{orderId}/payments")
    @Operation(
            summary = "Get order payments",
            description = "Get all payments and payment summary for an order"
    )
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> getOrderPayments(
            @PathVariable Long orderId) {

        log.info("Getting payments for order: {}", orderId);

        PaymentResponseDTO summary = paymentService.getPOSPaymentSummary(orderId);

        return ResponseEntity.ok(ApiResponse.success("Payments retrieved", summary));
    }

    @PostMapping("/{orderId}/refund")
    @Operation(
            summary = "Process refund",
            description = "Process a full, partial, or item-based refund for an order"
    )
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> processRefund(
            @PathVariable Long orderId,
            @Valid @RequestBody RefundRequestDTO request) {

        log.info("Processing refund for order {}: type={}", orderId, request.getType());

        PaymentResponseDTO response = paymentService.processPOSRefund(orderId, request);

        return ResponseEntity.ok(ApiResponse.success("Refund processed successfully", response));
    }

    @PostMapping("/{orderId}/void")
    @Operation(
            summary = "Void order",
            description = "Void an order completely (cancels all payments)"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> voidOrder(
            @PathVariable Long orderId,
            @RequestParam String reason,
            @RequestParam String voidedBy) {

        log.info("Voiding order {}: reason={}", orderId, reason);

        paymentService.voidOrder(orderId, reason, voidedBy);

        // Return updated order
        POSOrderResponse order = posOrderService.getOrderById(orderId);

        return ResponseEntity.ok(ApiResponse.success("Order voided successfully", order));
    }

    @PostMapping("/{orderId}/tip")
    @Operation(
            summary = "Add tip",
            description = "Add or update tip for an order"
    )
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> addTip(
            @PathVariable Long orderId,
            @RequestParam java.math.BigDecimal tipAmount) {

        log.info("Adding tip to order {}: amount={}", orderId, tipAmount);

        PaymentResponseDTO summary = paymentService.addTip(orderId, tipAmount);

        return ResponseEntity.ok(ApiResponse.success("Tip added successfully", summary));
    }

    @PostMapping("/{orderId}/close")
    @Operation(
            summary = "Close order and release table",
            description = "Close a dine-in order and mark the associated table(s) as available"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> closeOrder(
            @PathVariable Long orderId) {

        log.info("Closing order and releasing table: orderId={}", orderId);

        POSOrderResponse response = posOrderService.closeOrderAndReleaseTable(orderId);

        return ResponseEntity.ok(ApiResponse.success("Order closed and table released", response));
    }
}
