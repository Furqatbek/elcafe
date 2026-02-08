package com.elcafe.modules.financial.entity;

import com.elcafe.modules.inventory.entity.Supplier;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "FinancialPurchaseOrder")
@Table(name = "financial_purchase_orders", indexes = {
        @Index(name = "idx_purchase_order_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_purchase_order_number", columnList = "po_number"),
        @Index(name = "idx_purchase_order_supplier", columnList = "supplier_name"),
        @Index(name = "idx_purchase_order_supplier_id", columnList = "supplier_id"),
        @Index(name = "idx_purchase_order_date", columnList = "order_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, unique = true, length = 50)
    private String poNumber; // e.g., "PO-2025-001"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(nullable = false, length = 200)
    private String supplierName;

    @Column(length = 200)
    private String supplierContact;

    @Column(length = 500)
    private String supplierAddress;

    @Column(nullable = false)
    private LocalDate orderDate;

    @Column
    private LocalDate expectedDeliveryDate;

    @Column
    private LocalDate actualDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(length = 1000)
    private String notes;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 100)
    private String approvedBy;

    @Column
    private LocalDateTime approvedAt;

    @Column(length = 100)
    private String receivedBy;

    @Column
    private LocalDateTime receivedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        ORDERED,
        PARTIALLY_RECEIVED,
        RECEIVED,
        CANCELLED
    }

    public enum PaymentStatus {
        UNPAID,
        PARTIALLY_PAID,
        PAID
    }

    /**
     * Initialize default values before persisting
     */
    @PrePersist
    @PreUpdate
    protected void initializeDefaults() {
        if (subtotal == null) {
            subtotal = BigDecimal.ZERO;
        }
        if (taxAmount == null) {
            taxAmount = BigDecimal.ZERO;
        }
        if (shippingCost == null) {
            shippingCost = BigDecimal.ZERO;
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
        if (status == null) {
            status = Status.DRAFT;
        }
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.UNPAID;
        }
    }

    /**
     * Add an item to this purchase order
     */
    public void addItem(PurchaseOrderItem item) {
        items.add(item);
        item.setPurchaseOrder(this);
        recalculateTotals();
    }

    /**
     * Recalculate totals based on items
     */
    public void recalculateTotals() {
        subtotal = items.stream()
                .map(PurchaseOrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalAmount = subtotal.add(taxAmount != null ? taxAmount : BigDecimal.ZERO)
                              .add(shippingCost != null ? shippingCost : BigDecimal.ZERO);
    }

    /**
     * Update payment status based on paid amount
     */
    public void updatePaymentStatus() {
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) == 0) {
            paymentStatus = PaymentStatus.UNPAID;
        } else if (paidAmount.compareTo(totalAmount) >= 0) {
            paymentStatus = PaymentStatus.PAID;
        } else {
            paymentStatus = PaymentStatus.PARTIALLY_PAID;
        }
    }
}
