package com.elcafe.modules.financial.controller;

import com.elcafe.modules.financial.dto.*;
import com.elcafe.modules.financial.entity.PurchaseOrder;
import com.elcafe.modules.financial.entity.PurchaseOrderItem;
import com.elcafe.modules.financial.service.PurchaseOrderService;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/financial/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final RestaurantRepository restaurantRepository;
    private final InventoryIngredientRepository ingredientRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderRequest request) {
        log.info("Creating purchase order for restaurant: {}", request.getRestaurantId());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        PurchaseOrder po = PurchaseOrder.builder()
                .restaurant(restaurant)
                .supplierName(request.getSupplierName())
                .supplierContact(request.getSupplierContact())
                .supplierAddress(request.getSupplierAddress())
                .orderDate(request.getOrderDate())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .taxAmount(request.getTaxAmount())
                .shippingCost(request.getShippingCost())
                .notes(request.getNotes())
                .build();

        List<PurchaseOrderItem> items = request.getItems().stream()
                .map(itemReq -> {
                    PurchaseOrderItem item = PurchaseOrderItem.builder()
                            .itemName(itemReq.getItemName())
                            .description(itemReq.getDescription())
                            .sku(itemReq.getSku())
                            .quantity(itemReq.getQuantity())
                            .unit(itemReq.getUnit())
                            .unitPrice(itemReq.getUnitPrice())
                            .notes(itemReq.getNotes())
                            .build();

                    if (itemReq.getIngredientId() != null) {
                        Ingredient ingredient = ingredientRepository.findById(itemReq.getIngredientId())
                                .orElse(null);
                        item.setIngredient(ingredient);
                    }

                    return item;
                })
                .collect(Collectors.toList());

        PurchaseOrder createdPo = purchaseOrderService.createPurchaseOrder(po, items);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Purchase order created successfully", mapToResponse(createdPo)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PurchaseOrderResponse>>> getPurchaseOrders(
            @RequestParam Long restaurantId) {
        log.info("Getting purchase orders for restaurant: {}", restaurantId);

        List<PurchaseOrder> pos = purchaseOrderService.getPurchaseOrdersByRestaurant(restaurantId);
        List<PurchaseOrderResponse> responses = pos.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Purchase orders retrieved successfully", responses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getPurchaseOrderById(@PathVariable Long id) {
        log.info("Getting purchase order: {}", id);

        PurchaseOrder po = purchaseOrderService.getPurchaseOrderById(id);
        return ResponseEntity.ok(ApiResponse.success("Purchase order retrieved successfully", mapToResponse(po)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> approvePurchaseOrder(
            @PathVariable Long id,
            @RequestParam String approvedBy) {
        log.info("Approving purchase order: {}", id);

        PurchaseOrder po = purchaseOrderService.approvePurchaseOrder(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success("Purchase order approved successfully", mapToResponse(po)));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> receivePurchaseOrder(
            @PathVariable Long id,
            @Valid @RequestBody ReceivePurchaseOrderRequest request) {
        log.info("Receiving purchase order: {}", id);

        List<PurchaseOrderService.ReceivedItem> receivedItems = request.getItems().stream()
                .map(item -> {
                    PurchaseOrderService.ReceivedItem ri = new PurchaseOrderService.ReceivedItem();
                    ri.itemId = item.getItemId();
                    ri.receivedQuantity = item.getReceivedQuantity();
                    ri.notes = item.getNotes();
                    return ri;
                })
                .collect(Collectors.toList());

        PurchaseOrder po = purchaseOrderService.receivePurchaseOrder(
                id, request.getActualDeliveryDate(), request.getReceivedBy(), receivedItems);

        return ResponseEntity.ok(ApiResponse.success("Purchase order received successfully", mapToResponse(po)));
    }

    @PostMapping("/{id}/payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> recordPayment(
            @PathVariable Long id,
            @Valid @RequestBody RecordPaymentRequest request) {
        log.info("Recording payment for purchase order: {}", id);

        PurchaseOrder po = purchaseOrderService.recordPayment(
                id, request.getPaymentDate(), request.getAmount(),
                request.getPaymentMethod(), request.getRecordedBy());

        return ResponseEntity.ok(ApiResponse.success("Payment recorded successfully", mapToResponse(po)));
    }

    private PurchaseOrderResponse mapToResponse(PurchaseOrder po) {
        List<PurchaseOrderItemResponse> itemResponses = po.getItems().stream()
                .map(item -> PurchaseOrderItemResponse.builder()
                        .id(item.getId())
                        .purchaseOrderId(po.getId())
                        .ingredientId(item.getIngredient() != null ? item.getIngredient().getId() : null)
                        .ingredientName(item.getIngredient() != null ? item.getIngredient().getName() : null)
                        .itemName(item.getItemName())
                        .description(item.getDescription())
                        .sku(item.getSku())
                        .quantity(item.getQuantity())
                        .unit(item.getUnit())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .receivedQuantity(item.getReceivedQuantity())
                        .fullyReceived(item.isFullyReceived())
                        .notes(item.getNotes())
                        .build())
                .collect(Collectors.toList());

        return PurchaseOrderResponse.builder()
                .id(po.getId())
                .restaurantId(po.getRestaurant().getId())
                .restaurantName(po.getRestaurant().getName())
                .poNumber(po.getPoNumber())
                .supplierName(po.getSupplierName())
                .supplierContact(po.getSupplierContact())
                .supplierAddress(po.getSupplierAddress())
                .orderDate(po.getOrderDate())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .actualDeliveryDate(po.getActualDeliveryDate())
                .status(po.getStatus())
                .subtotal(po.getSubtotal())
                .taxAmount(po.getTaxAmount())
                .shippingCost(po.getShippingCost())
                .totalAmount(po.getTotalAmount())
                .paidAmount(po.getPaidAmount())
                .paymentStatus(po.getPaymentStatus())
                .notes(po.getNotes())
                .createdBy(po.getCreatedBy())
                .approvedBy(po.getApprovedBy())
                .approvedAt(po.getApprovedAt())
                .receivedBy(po.getReceivedBy())
                .receivedAt(po.getReceivedAt())
                .items(itemResponses)
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
