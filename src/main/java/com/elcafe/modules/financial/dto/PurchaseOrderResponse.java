package com.elcafe.modules.financial.dto;

import com.elcafe.modules.financial.entity.PurchaseOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String poNumber;
    private Long supplierId;
    private String supplierName;
    private String supplierContact;
    private String supplierAddress;
    private String supplierPaymentTerms;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private LocalDate actualDeliveryDate;
    private PurchaseOrder.Status status;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingCost;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private PurchaseOrder.PaymentStatus paymentStatus;
    private String notes;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String receivedBy;
    private LocalDateTime receivedAt;
    private List<PurchaseOrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
