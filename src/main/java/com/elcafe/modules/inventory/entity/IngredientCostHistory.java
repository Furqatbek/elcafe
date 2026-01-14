package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.inventory.enums.CostChangeReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tracks historical cost changes for ingredients.
 * Enables historical COGS calculations and cost auditing.
 */
@Entity
@Table(name = "ingredient_cost_history", indexes = {
        @Index(name = "idx_cost_history_ingredient", columnList = "ingredient_id"),
        @Index(name = "idx_cost_history_effective", columnList = "effective_from"),
        @Index(name = "idx_cost_history_batch", columnList = "batch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientCostHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal previousCost;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal newCost;

    @Column(precision = 15, scale = 4)
    private BigDecimal weightedAverageCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CostChangeReason reason;

    @Column(nullable = false)
    private LocalDateTime effectiveFrom;

    @Column
    private LocalDateTime effectiveTo;

    // Optional reference to batch that caused the cost change
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private InventoryBatch batch;

    // Optional reference to purchase order
    @Column
    private Long purchaseOrderId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Calculate the percentage change in cost
     */
    public BigDecimal getCostChangePercent() {
        if (previousCost == null || previousCost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }
        return newCost.subtract(previousCost)
                .divide(previousCost, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Check if this is a cost increase
     */
    public boolean isCostIncrease() {
        return newCost.compareTo(previousCost) > 0;
    }
}
