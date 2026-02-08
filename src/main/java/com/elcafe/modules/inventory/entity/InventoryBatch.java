package com.elcafe.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_batches", indexes = {
        @Index(name = "idx_batch_ingredient", columnList = "ingredient_id"),
        @Index(name = "idx_batch_expiry", columnList = "expiry_date"),
        @Index(name = "idx_batch_status", columnList = "status"),
        @Index(name = "idx_batch_fefo", columnList = "ingredient_id, status, quantity, expiry_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Version field for optimistic locking - prevents race conditions
     * when multiple transactions try to consume from the same batch concurrently
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(nullable = false, length = 100)
    private String batchNumber;

    @Column(nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal initialQuantity = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate receivedDate;

    @Column
    private LocalDate expiryDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal costPerUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(length = 100)
    private String poReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        ACTIVE,      // In use, can be consumed
        EXPIRED,     // Past expiry date
        DEPLETED,    // Quantity is 0
        WRITTEN_OFF  // Manually removed (waste, damage, etc.)
    }

    /**
     * Check if this batch is expired
     */
    public boolean isExpired() {
        return expiryDate != null && LocalDate.now().isAfter(expiryDate);
    }

    /**
     * Check if this batch is expiring within the given days
     */
    public boolean isExpiringSoon(int days) {
        if (expiryDate == null) return false;
        LocalDate alertDate = LocalDate.now().plusDays(days);
        return !isExpired() && !expiryDate.isAfter(alertDate);
    }

    /**
     * Get days until expiry (negative if already expired)
     */
    public long getDaysUntilExpiry() {
        if (expiryDate == null) return Long.MAX_VALUE;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    /**
     * Check if batch has available quantity
     */
    public boolean hasStock() {
        return quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if batch can be consumed (active, not expired, has stock)
     */
    public boolean isConsumable() {
        return status == Status.ACTIVE && !isExpired() && hasStock();
    }

    /**
     * Consume quantity from this batch
     * @return actual quantity consumed (may be less if not enough stock)
     */
    public BigDecimal consume(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal available = quantity != null ? quantity : BigDecimal.ZERO;
        BigDecimal consumed = amount.min(available);

        this.quantity = available.subtract(consumed);

        // Update status if depleted
        if (this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = Status.DEPLETED;
        }

        return consumed;
    }

    /**
     * Add quantity to this batch (e.g., for corrections)
     */
    public void addQuantity(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        this.quantity = (this.quantity != null ? this.quantity : BigDecimal.ZERO).add(amount);

        // Reactivate if was depleted
        if (this.status == Status.DEPLETED && this.quantity.compareTo(BigDecimal.ZERO) > 0) {
            this.status = Status.ACTIVE;
        }
    }

    /**
     * Mark batch as expired
     */
    public void markExpired() {
        this.status = Status.EXPIRED;
    }

    /**
     * Write off batch (waste, damage, etc.)
     */
    public void writeOff(String reason) {
        this.status = Status.WRITTEN_OFF;
        this.notes = (this.notes != null ? this.notes + "\n" : "") + "Written off: " + reason;
    }
}
