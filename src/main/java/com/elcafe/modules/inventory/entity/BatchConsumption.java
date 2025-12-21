package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.inventory.enums.ValuationMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records which batches were consumed for each transaction.
 * Links orders/transactions to specific batch costs for accurate COGS.
 */
@Entity
@Table(name = "batch_consumptions", indexes = {
        @Index(name = "idx_consumption_batch", columnList = "batch_id"),
        @Index(name = "idx_consumption_transaction", columnList = "transaction_id"),
        @Index(name = "idx_consumption_order", columnList = "order_id"),
        @Index(name = "idx_consumption_ingredient", columnList = "ingredient_id"),
        @Index(name = "idx_consumption_date", columnList = "consumed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private InventoryBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private InventoryTransaction transaction;

    // Direct order reference for faster COGS queries
    @Column
    private Long orderId;

    // Order item reference for granular tracking
    @Column
    private Long orderItemId;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal costPerUnit;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValuationMethod valuationMethod;

    @Column(nullable = false)
    private LocalDateTime consumedAt;

    // Batch info snapshot (for historical reference if batch is deleted)
    @Column(length = 100)
    private String batchNumber;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Create a consumption record from a batch
     */
    public static BatchConsumption fromBatch(InventoryBatch batch, BigDecimal quantity,
                                              ValuationMethod method, Long orderId) {
        BigDecimal cost = batch.getCostPerUnit() != null ? batch.getCostPerUnit() : BigDecimal.ZERO;
        return BatchConsumption.builder()
                .ingredient(batch.getIngredient())
                .batch(batch)
                .quantity(quantity)
                .costPerUnit(cost)
                .totalCost(cost.multiply(quantity))
                .valuationMethod(method)
                .orderId(orderId)
                .consumedAt(LocalDateTime.now())
                .batchNumber(batch.getBatchNumber())
                .build();
    }
}
