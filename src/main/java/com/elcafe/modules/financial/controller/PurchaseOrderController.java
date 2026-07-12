package com.elcafe.modules.financial.controller;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.financial.dto.*;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.entity.PurchaseOrder;
import com.elcafe.modules.financial.entity.PurchaseOrderItem;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.service.PurchaseOrderService;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.Supplier;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.SupplierRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/financial/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final RestaurantAuthorizationService restaurantAuthorizationService;
    private final PurchaseOrderService purchaseOrderService;
    private final RestaurantRepository restaurantRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final SupplierRepository supplierRepository;
    private final ExpenseRepository expenseRepository;
    private final com.elcafe.modules.pos.shift.service.ShiftEnforcementService shiftEnforcementService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'WAITER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderRequest request) {
        // Enforce active shift for operators/waiters
        shiftEnforcementService.requireActiveShift();

        log.info("Creating purchase order for restaurant: {}", request.getRestaurantId());
        restaurantAuthorizationService.checkAccess(request.getRestaurantId());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        Supplier supplier = null;
        String supplierName = request.getSupplierName();
        String supplierContact = request.getSupplierContact();
        String supplierAddress = request.getSupplierAddress();

        // Default supplier name if not provided
        if (supplierName == null || supplierName.isBlank()) {
            supplierName = "Unknown Supplier";
        }

        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + request.getSupplierId()));
            // Auto-populate from supplier entity if not provided
            if (supplierName == null || supplierName.isBlank()) {
                supplierName = supplier.getName();
            }
            if (supplierContact == null || supplierContact.isBlank()) {
                supplierContact = supplier.getContactPerson() != null ?
                        supplier.getContactPerson() + (supplier.getPhone() != null ? " - " + supplier.getPhone() : "") :
                        supplier.getPhone();
            }
            if (supplierAddress == null || supplierAddress.isBlank()) {
                supplierAddress = supplier.getAddress();
            }
        }

        PurchaseOrder po = PurchaseOrder.builder()
                .restaurant(restaurant)
                .supplier(supplier)
                .supplierName(supplierName)
                .supplierContact(supplierContact)
                .supplierAddress(supplierAddress)
                .orderDate(request.getOrderDate())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .taxAmount(request.getTaxAmount() != null ? request.getTaxAmount() : BigDecimal.ZERO)
                .shippingCost(request.getShippingCost() != null ? request.getShippingCost() : BigDecimal.ZERO)
                .notes(request.getNotes())
                .build();

        List<PurchaseOrderItem> items = request.getItems().stream()
                .map(itemReq -> {
                    BigDecimal quantity = itemReq.getQuantity() != null ? itemReq.getQuantity() : BigDecimal.ZERO;
                    BigDecimal unitPrice = itemReq.getUnitPrice() != null ? itemReq.getUnitPrice() : BigDecimal.ZERO;
                    BigDecimal totalPrice = quantity.multiply(unitPrice);

                    PurchaseOrderItem item = PurchaseOrderItem.builder()
                            .itemName(itemReq.getItemName())
                            .description(itemReq.getDescription())
                            .sku(itemReq.getSku())
                            .quantity(quantity)
                            .unit(itemReq.getUnit() != null ? itemReq.getUnit() : "")
                            .unitPrice(unitPrice)
                            .totalPrice(totalPrice)
                            .receivedQuantity(BigDecimal.ZERO)
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

        PurchaseOrder createdPo;
        if (Boolean.TRUE.equals(request.getAutoFinalize())) {
            // One-shot path: caller wants the PO marked as RECEIVED and
            // fully PAID immediately. Used for typical "just-bought-this
            // at the market" purchases where there's no review step.
            String performedBy = "OPERATOR";
            createdPo = purchaseOrderService.createAndFinalize(
                    po, items,
                    request.getPaymentMethod(),
                    request.getPaymentDate(),
                    performedBy);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Purchase order created, received and paid",
                            mapToResponse(createdPo)));
        }

        createdPo = purchaseOrderService.createPurchaseOrder(po, items);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Purchase order created successfully", mapToResponse(createdPo)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'WAITER')")
    public ResponseEntity<ApiResponse<List<PurchaseOrderResponse>>> getPurchaseOrders(
            @RequestParam Long restaurantId) {
        log.info("Getting purchase orders for restaurant: {}", restaurantId);
        restaurantAuthorizationService.checkAccess(restaurantId);

        List<PurchaseOrder> pos = purchaseOrderService.getPurchaseOrdersByRestaurant(restaurantId);
        List<PurchaseOrderResponse> responses = pos.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Purchase orders retrieved successfully", responses));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'WAITER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getPurchaseOrderById(@PathVariable Long id) {
        log.info("Getting purchase order: {}", id);

        PurchaseOrder po = purchaseOrderService.getPurchaseOrderById(id);
        return ResponseEntity.ok(ApiResponse.success("Purchase order retrieved successfully", mapToResponse(po)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> approvePurchaseOrder(
            @PathVariable Long id,
            @RequestParam String approvedBy) {
        log.info("Approving purchase order: {}", id);

        PurchaseOrder po = purchaseOrderService.approvePurchaseOrder(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success("Purchase order approved successfully", mapToResponse(po)));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
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

        // Look up linked expense
        Expense linkedExpense = expenseRepository.findByPurchaseOrderId(po.getId()).orElse(null);

        Supplier supplierEntity = po.getSupplier();
        return PurchaseOrderResponse.builder()
                .id(po.getId())
                .restaurantId(po.getRestaurant().getId())
                .restaurantName(po.getRestaurant().getName())
                .poNumber(po.getPoNumber())
                .supplierId(supplierEntity != null ? supplierEntity.getId() : null)
                .supplierName(po.getSupplierName())
                .supplierContact(po.getSupplierContact())
                .supplierAddress(po.getSupplierAddress())
                .supplierPaymentTerms(supplierEntity != null ? supplierEntity.getPaymentTerms() : null)
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
                .expenseId(linkedExpense != null ? linkedExpense.getId() : null)
                .expenseNumber(linkedExpense != null ? linkedExpense.getExpenseNumber() : null)
                .build();
    }
}
