package com.elcafe.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_count_items", indexes = {
        @Index(name = "idx_stock_count_items_count", columnList = "stock_count_id"),
        @Index(name = "idx_stock_count_items_ingredient", columnList = "ingredient_id"),
        @Index(name = "idx_stock_count_items_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_count_id", nullable = false)
    private StockCount stockCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "system_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal systemQuantity;

    @Column(name = "counted_quantity", precision = 10, scale = 3)
    private BigDecimal countedQuantity;

    @Column(name = "variance_quantity", precision = 10, scale = 3)
    private BigDecimal varianceQuantity;

    @Column(name = "variance_value", precision = 15, scale = 2)
    private BigDecimal varianceValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "variance_reason", length = 50)
    private VarianceReason varianceReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "counted_by", length = 100)
    private String countedBy;

    @Column(name = "counted_at")
    private LocalDateTime countedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,    // Not yet counted
        COUNTED,    // Counted once
        RECOUNTED,  // Recounted due to variance
        VERIFIED    // Verified by reviewer
    }

    public enum VarianceReason {
        THEFT,              // Theft/Pilferage
        DAMAGE,             // Physical damage
        SPOILAGE,           // Spoilage/Expiry
        COUNTING_ERROR,     // Counting error (previous count was wrong)
        SYSTEM_ERROR,       // System data was incorrect
        UNRECORDED_USAGE,   // Usage not recorded in system
        UNRECORDED_RECEIPT, // Receipt not recorded in system
        SHRINKAGE,          // General shrinkage
        OTHER               // Other reason
    }

    /**
     * Record a count and calculate variance
     */
    public void recordCount(BigDecimal counted, String countedBy, String notes) {
        this.countedQuantity = counted;
        this.countedBy = countedBy;
        this.notes = notes;
        this.countedAt = LocalDateTime.now();
        this.status = Status.COUNTED;
        calculateVariance();
    }

    /**
     * Calculate variance between system and counted quantities
     * Uses ingredient's effective cost (WAC if available) for variance value
     */
    public void calculateVariance() {
        if (countedQuantity != null && systemQuantity != null) {
            this.varianceQuantity = countedQuantity.subtract(systemQuantity);

            // Calculate variance value using ingredient's effective cost (WAC or costPerUnit)
            if (ingredient != null) {
                BigDecimal effectiveCost = ingredient.getEffectiveCost();
                if (effectiveCost != null && effectiveCost.compareTo(BigDecimal.ZERO) > 0) {
                    this.varianceValue = varianceQuantity.multiply(effectiveCost)
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
        }
    }

    /**
     * Check if there is a variance
     */
    public boolean hasVariance() {
        return varianceQuantity != null &&
               varianceQuantity.compareTo(BigDecimal.ZERO) != 0;
    }

    /**
     * Get variance percentage
     */
    public BigDecimal getVariancePercentage() {
        if (systemQuantity == null || systemQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return countedQuantity != null && countedQuantity.compareTo(BigDecimal.ZERO) != 0
                    ? new BigDecimal("100")
                    : BigDecimal.ZERO;
        }
        if (varianceQuantity == null) {
            return BigDecimal.ZERO;
        }
        return varianceQuantity.abs()
                .divide(systemQuantity.abs(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
