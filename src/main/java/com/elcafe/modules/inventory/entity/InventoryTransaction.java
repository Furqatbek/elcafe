package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.inventory.enums.TransactionType;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "inventory_transactions", indexes = {
        @Index(name = "idx_inv_txn_ingredient", columnList = "ingredient_id"),
        @Index(name = "idx_inv_txn_reference", columnList = "reference_type, reference_id"),
        @Index(name = "idx_inv_txn_created_at", columnList = "created_at"),
        @Index(name = "idx_inv_txn_batch", columnList = "batch_id")
})
@EntityListeners(AuditingEntityListener.class)
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TransactionType type;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal balanceBefore;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal balanceAfter;

    @Column(name = "reference_type", length = 50)
    private String referenceType; // ORDER, PURCHASE, ADJUSTMENT, WASTE, etc.

    @Column(name = "reference_id")
    private Long referenceId; // Order ID, Purchase ID, etc.

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 100)
    private String performedBy;

    // Cost tracking fields for inventory valuation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private InventoryBatch batch;

    @Column(precision = 15, scale = 4)
    private BigDecimal costPerUnit;

    @Column(precision = 15, scale = 4)
    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ValuationMethod valuationMethod;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Calculate total cost from quantity and cost per unit
     */
    public void calculateTotalCost() {
        if (costPerUnit != null && quantity != null) {
            this.totalCost = costPerUnit.multiply(quantity.abs());
        }
    }
}
