package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "production_batches", indexes = {
        @Index(name = "idx_production_batch_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_production_batch_product", columnList = "product_id"),
        @Index(name = "idx_production_batch_status", columnList = "status"),
        @Index(name = "idx_production_batch_expires", columnList = "expires_at"),
        @Index(name = "idx_production_batch_fefo", columnList = "product_id, status, remaining_quantity, expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 50, unique = true)
    private String batchNumber;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal outputQuantity = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String outputUnit;

    @Column(nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal remainingQuantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal totalInputCost = BigDecimal.ZERO;

    @Column(precision = 15, scale = 4)
    private BigDecimal costPerUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(length = 100)
    private String preparedBy;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime expiresAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "productionBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductionBatchInput> inputs = new ArrayList<>();

    @OneToMany(mappedBy = "productionBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductionBatchConsumption> consumptions = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        DRAFT,
        IN_PROGRESS,
        READY,
        SERVING,
        DEPLETED,
        EXPIRED,
        WASTED
    }

    /**
     * Consume a quantity from this batch. Decrements remaining and returns the cost.
     * @throws IllegalStateException if insufficient remaining quantity
     */
    public BigDecimal consume(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Consume quantity must be positive");
        }
        if (remainingQuantity.compareTo(quantity) < 0) {
            throw new IllegalStateException(
                    String.format("Insufficient remaining quantity: requested %s but only %s available in batch %s",
                            quantity, remainingQuantity, batchNumber));
        }
        this.remainingQuantity = this.remainingQuantity.subtract(quantity);
        if (this.remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            this.status = Status.DEPLETED;
        } else if (this.status == Status.READY) {
            this.status = Status.SERVING;
        }
        return costPerUnit != null ? costPerUnit.multiply(quantity) : BigDecimal.ZERO;
    }

    public boolean isAvailable() {
        return (status == Status.READY || status == Status.SERVING)
                && remainingQuantity.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getRemaining() {
        return remainingQuantity;
    }

    public BigDecimal calculateCostPerUnit() {
        if (outputQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return totalInputCost.divide(outputQuantity, 4, RoundingMode.HALF_UP);
    }
}
