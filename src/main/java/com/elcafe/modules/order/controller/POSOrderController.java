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
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.service.PaymentService;
import com.elcafe.modules.order.service.POSOrderService;
import com.elcafe.modules.order.service.POSOrderItemService;
import com.elcafe.modules.order.service.POSOrderDiscountService;
import com.elcafe.modules.order.service.POSOrderFeeService;
import com.elcafe.modules.order.service.POSSplitBillService;
import com.elcafe.modules.order.service.POSTableService;
import com.elcafe.modules.order.service.IdempotencyService;
import com.elcafe.modules.promotion.dto.ApplyDiscountRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponResponse;
import com.elcafe.modules.promotion.dto.ActiveHappyHourResponse;
import com.elcafe.modules.promotion.service.HappyHourService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for POS Order operations.
 * Delegates to focused services for specific responsibilities:
 * - POSOrderService: Core order creation and retrieval
 * - POSOrderItemService: Item management
 * - POSOrderDiscountService: Discount operations
 * - POSOrderFeeService: Fee management
 * - POSSplitBillService: Bill splitting
 * - POSTableService: Table management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pos/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
@Tag(name = "POS Orders", description = "Point of Sale system endpoints for in-store orders")
public class POSOrderController {

    private final POSOrderService posOrderService;
    private final POSOrderItemService posOrderItemService;
    private final POSOrderDiscountService posOrderDiscountService;
    private final POSOrderFeeService posOrderFeeService;
    private final POSSplitBillService posSplitBillService;
    private final POSTableService posTableService;
    private final PaymentService paymentService;
    private final HappyHourService happyHourService;
    private final IdempotencyService idempotencyService;

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

    // ============== Item Management Endpoints ==============

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

        Order order = posOrderItemService.addItemToOrder(orderId, request);
        POSOrderResponse response = posOrderService.mapToResponse(order, posOrderService.getOrderTypeString(order));

        return ResponseEntity.ok(ApiResponse.success("Item added to order", response));
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

        Order order = posOrderItemService.removeItemFromOrder(orderId, itemId);
        POSOrderResponse response = posOrderService.mapToResponse(order, posOrderService.getOrderTypeString(order));

        return ResponseEntity.ok(ApiResponse.success("Item removed from order", response));
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

        Order order = posOrderItemService.updateItemQuantity(orderId, itemId, quantity);
        POSOrderResponse response = posOrderService.mapToResponse(order, posOrderService.getOrderTypeString(order));

        return ResponseEntity.ok(ApiResponse.success("Item quantity updated", response));
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

        SplitBillDTO.SplitBillResponse response = posSplitBillService.splitBill(orderId, request);

        return ResponseEntity.ok(ApiResponse.success("Bill split successfully", response));
    }

    // ============== Payment Endpoints ==============

    @PostMapping("/{orderId}/payments")
    @Operation(
            summary = "Process payment",
            description = "Process a payment for an order (supports split payments). " +
                    "Include an idempotencyKey in the request to prevent double-charging on retries."
    )
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> processPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequestDTO request) {

        log.info("Processing payment for order {}: method={}, amount={}, idempotencyKey={}",
                orderId, request.getMethod(), request.getAmount(), request.getIdempotencyKey());

        // Use idempotency service to prevent double-charging
        IdempotencyService.IdempotentResult<PaymentResponseDTO> result = idempotencyService.executeIdempotently(
                request.getIdempotencyKey(),
                "PAYMENT",
                request,
                () -> paymentService.processPOSPayment(orderId, request),
                PaymentResponseDTO.class
        );

        String message = result.fromCache()
                ? "Payment already processed (idempotent replay)"
                : "Payment processed successfully";

        return ResponseEntity.ok(ApiResponse.success(message, result.result()));
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

    // ============== Fee Management Endpoints ==============

    @PostMapping("/{orderId}/service-fee")
    @Operation(
            summary = "Apply service fee",
            description = "Apply service fee to an order by specifying the percentage"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> applyServiceFee(
            @PathVariable Long orderId,
            @RequestParam java.math.BigDecimal serviceFeePercent) {

        log.info("Applying service fee to order {}: percent={}", orderId, serviceFeePercent);

        Order order = posOrderFeeService.applyServiceFee(orderId, serviceFeePercent);
        POSOrderResponse response = posOrderService.mapToResponse(order, posOrderService.getOrderTypeString(order));

        return ResponseEntity.ok(ApiResponse.success("Service fee applied successfully", response));
    }

    @PostMapping("/{orderId}/service-fee-amount")
    @Operation(
            summary = "Apply service fee by fixed amount",
            description = "Apply service fee to an order by specifying a fixed amount"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> applyServiceFeeAmount(
            @PathVariable Long orderId,
            @RequestParam java.math.BigDecimal serviceFeeAmount) {

        log.info("Applying service fee amount to order {}: amount={}", orderId, serviceFeeAmount);

        Order order = posOrderFeeService.applyServiceFeeAmount(orderId, serviceFeeAmount);
        POSOrderResponse response = posOrderService.mapToResponse(order, posOrderService.getOrderTypeString(order));

        return ResponseEntity.ok(ApiResponse.success("Service fee applied successfully", response));
    }

    @PostMapping("/{orderId}/entry-fee")
    @Operation(
            summary = "Apply entry fee",
            description = "Apply entry fee to an order by specifying the amount"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> applyEntryFee(
            @PathVariable Long orderId,
            @RequestParam java.math.BigDecimal entryFee) {

        log.info("Applying entry fee to order {}: amount={}", orderId, entryFee);

        Order order = posOrderFeeService.applyEntryFee(orderId, entryFee);
        POSOrderResponse response = posOrderService.mapToResponse(order, posOrderService.getOrderTypeString(order));

        return ResponseEntity.ok(ApiResponse.success("Entry fee applied successfully", response));
    }

    // ============== Table Management Endpoints ==============

    @PostMapping("/{orderId}/close")
    @Operation(
            summary = "Close order and release table",
            description = "Close a dine-in order and mark the associated table(s) as available"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> closeOrder(
            @PathVariable Long orderId) {

        log.info("Closing order and releasing table: orderId={}", orderId);

        Order order = posTableService.closeOrderAndReleaseTable(orderId);
        POSOrderResponse response = posOrderService.mapToResponse(order, posOrderService.getOrderTypeString(order));

        return ResponseEntity.ok(ApiResponse.success("Order closed and table released", response));
    }

    @PatchMapping("/{orderId}/change-table")
    @Operation(
            summary = "Change order table",
            description = "Move an order to a different table"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> changeTable(
            @PathVariable Long orderId,
            @RequestParam Long newTableId) {

        log.info("Changing table for order {}: newTableId={}", orderId, newTableId);

        Order order = posTableService.changeTable(orderId, newTableId);
        POSOrderResponse response = posOrderService.mapToResponse(order, "DINE_IN");

        return ResponseEntity.ok(ApiResponse.success("Order moved to new table successfully", response));
    }

    // ============== Discount/Promotion Endpoints ==============

    @PostMapping("/{orderId}/discount")
    @Operation(
            summary = "Apply discount to order",
            description = "Apply a coupon code, promotion, manual discount, or happy hour discount to an order"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> applyDiscount(
            @PathVariable Long orderId,
            @Valid @RequestBody ApplyDiscountRequest request) {

        log.info("Applying discount to order {}: type={}", orderId, request.getDiscountType());

        Order order = posOrderDiscountService.applyDiscount(orderId, request);
        POSOrderResponse response = posOrderService.mapToResponse(order, posOrderService.getOrderTypeString(order));

        return ResponseEntity.ok(ApiResponse.success("Discount applied successfully", response));
    }

    @DeleteMapping("/{orderId}/discount")
    @Operation(
            summary = "Remove discount from order",
            description = "Remove any applied discount from an order"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> removeDiscount(
            @PathVariable Long orderId) {

        log.info("Removing discount from order {}", orderId);

        Order order = posOrderDiscountService.removeDiscount(orderId);
        POSOrderResponse response = posOrderService.mapToResponse(order, posOrderService.getOrderTypeString(order));

        return ResponseEntity.ok(ApiResponse.success("Discount removed successfully", response));
    }

    @PostMapping("/{orderId}/validate-coupon")
    @Operation(
            summary = "Validate coupon code",
            description = "Validate a coupon code for an order without applying it"
    )
    public ResponseEntity<ApiResponse<ValidateCouponResponse>> validateCoupon(
            @PathVariable Long orderId,
            @RequestParam String couponCode) {

        log.info("Validating coupon {} for order {}", couponCode, orderId);

        ValidateCouponResponse response = posOrderDiscountService.validateCoupon(orderId, couponCode);

        return ResponseEntity.ok(ApiResponse.success("Coupon validated", response));
    }

    // ============== Happy Hour Endpoints ==============

    @GetMapping("/happy-hour/active")
    @Operation(
            summary = "Get active happy hour",
            description = "Get currently active happy hour for a restaurant with discount preview"
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActiveHappyHour(
            @RequestParam Long restaurantId) {

        log.info("Getting active happy hour for restaurant: {}", restaurantId);

        Optional<ActiveHappyHourResponse> activeHappyHour = happyHourService.getActiveHappyHour(restaurantId);

        if (activeHappyHour.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("No active happy hour", null));
        }

        ActiveHappyHourResponse hh = activeHappyHour.get();
        Map<String, Object> response = Map.of(
                "id", hh.getId(),
                "name", hh.getName(),
                "discountPercent", hh.getDiscountPercent(),
                "endTime", hh.getEndsAt() != null ? hh.getEndsAt() : "",
                "appliesToAll", hh.getAppliesToAll() != null ? hh.getAppliesToAll() : true,
                "applicableProductIds", hh.getApplicableProductIds() != null ? hh.getApplicableProductIds() : List.of(),
                "applicableCategoryIds", hh.getApplicableCategoryIds() != null ? hh.getApplicableCategoryIds() : List.of()
        );

        return ResponseEntity.ok(ApiResponse.success("Active happy hour found", response));
    }

    @GetMapping("/{orderId}/happy-hour/preview")
    @Operation(
            summary = "Preview happy hour discount",
            description = "Calculate and preview happy hour discount for an order without applying it"
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewHappyHourDiscount(
            @PathVariable Long orderId) {

        log.info("Previewing happy hour discount for order: {}", orderId);

        POSOrderResponse order = posOrderService.getOrderById(orderId);
        Long restaurantId = order.getRestaurantId();

        Optional<ActiveHappyHourResponse> activeHappyHour = happyHourService.getActiveHappyHour(restaurantId);

        if (activeHappyHour.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("No active happy hour", Map.of(
                    "hasActiveHappyHour", false,
                    "discountAmount", BigDecimal.ZERO
            )));
        }

        // Calculate discount preview
        BigDecimal discount = posOrderDiscountService.calculateHappyHourDiscountPreview(orderId);
        ActiveHappyHourResponse hh = activeHappyHour.get();

        Map<String, Object> response = Map.of(
                "hasActiveHappyHour", true,
                "happyHourId", hh.getId(),
                "happyHourName", hh.getName(),
                "discountPercent", hh.getDiscountPercent(),
                "discountAmount", discount,
                "endTime", hh.getEndsAt() != null ? hh.getEndsAt() : ""
        );

        return ResponseEntity.ok(ApiResponse.success("Happy hour discount preview", response));
    }

    @PostMapping("/{orderId}/happy-hour/apply")
    @Operation(
            summary = "Apply happy hour discount",
            description = "Apply the active happy hour discount to an order"
    )
    public ResponseEntity<ApiResponse<POSOrderResponse>> applyHappyHourDiscount(
            @PathVariable Long orderId) {

        log.info("Applying happy hour discount to order: {}", orderId);

        POSOrderResponse orderResponse = posOrderService.getOrderById(orderId);
        Long restaurantId = orderResponse.getRestaurantId();

        Optional<ActiveHappyHourResponse> activeHappyHour = happyHourService.getActiveHappyHour(restaurantId);

        if (activeHappyHour.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("No active happy hour available"));
        }

        // Apply the happy hour discount using the existing discount mechanism
        ApplyDiscountRequest discountRequest = ApplyDiscountRequest.builder()
                .discountType(com.elcafe.modules.promotion.enums.DiscountType.HAPPY_HOUR)
                .happyHourId(activeHappyHour.get().getId())
                .build();

        Order order = posOrderDiscountService.applyDiscount(orderId, discountRequest);
        POSOrderResponse response = posOrderService.mapToResponse(order, posOrderService.getOrderTypeString(order));

        return ResponseEntity.ok(ApiResponse.success("Happy hour discount applied successfully", response));
    }
}
