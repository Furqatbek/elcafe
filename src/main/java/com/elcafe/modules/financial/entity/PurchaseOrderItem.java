package com.elcafe.modules.financial.entity;

import com.elcafe.modules.inventory.entity.Ingredient;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity(name = "FinancialPurchaseOrderItem")
@Table(name = "financial_purchase_order_items", indexes = {
        @Index(name = "idx_po_item_purchase_order", columnList = "purchase_order_id"),
        @Index(name = "idx_po_item_ingredient", columnList = "ingredient_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient; // Link to inventory ingredient (optional)

    @Column(nullable = false, length = 200)
    private String itemName;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String sku;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, length = 50)
    private String unit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;

    @Column(precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Calculate total price based on quantity and unit price
     */
    @PrePersist
    @PreUpdate
    public void calculateTotalPrice() {
        if (quantity != null && unitPrice != null) {
            totalPrice = quantity.multiply(unitPrice);
        }
    }

    /**
     * Check if item is fully received
     */
    public boolean isFullyReceived() {
        return receivedQuantity != null &&
               quantity != null &&
               receivedQuantity.compareTo(quantity) >= 0;
    }
}
