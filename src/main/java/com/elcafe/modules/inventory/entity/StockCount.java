package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_counts", indexes = {
        @Index(name = "idx_stock_counts_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_stock_counts_status", columnList = "status"),
        @Index(name = "idx_stock_counts_date", columnList = "scheduled_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "count_number", nullable = false, length = 50)
    private String countNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "count_type", nullable = false, length = 20)
    private CountType countType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;

    @Column(name = "counted_by", length = 100)
    private String countedBy;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "total_items")
    @Builder.Default
    private Integer totalItems = 0;

    @Column(name = "counted_items")
    @Builder.Default
    private Integer countedItems = 0;

    @Column(name = "variance_count")
    @Builder.Default
    private Integer varianceCount = 0;

    @Column(name = "total_variance_value", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalVarianceValue = BigDecimal.ZERO;

    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<StockCountItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum CountType {
        FULL,           // Full physical inventory count
        CYCLE,          // Cycle count (subset of items)
        SPOT_CHECK      // Quick spot check of specific items
    }

    public enum Status {
        DRAFT,          // Created but not started
        IN_PROGRESS,    // Counting in progress
        PENDING_REVIEW, // Counting complete, awaiting review
        APPROVED,       // Reviewed and approved, adjustments applied
        CANCELLED       // Cancelled
    }

    public void addItem(StockCountItem item) {
        items.add(item);
        item.setStockCount(this);
    }

    public void removeItem(StockCountItem item) {
        items.remove(item);
        item.setStockCount(null);
    }

    /**
     * Recalculate summary statistics
     */
    public void recalculateTotals() {
        this.totalItems = items.size();
        this.countedItems = (int) items.stream()
                .filter(i -> i.getStatus() == StockCountItem.Status.COUNTED ||
                            i.getStatus() == StockCountItem.Status.VERIFIED)
                .count();
        this.varianceCount = (int) items.stream()
                .filter(i -> i.getVarianceQuantity() != null &&
                            i.getVarianceQuantity().compareTo(BigDecimal.ZERO) != 0)
                .count();
        this.totalVarianceValue = items.stream()
                .filter(i -> i.getVarianceValue() != null)
                .map(StockCountItem::getVarianceValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Check if all items have been counted
     */
    public boolean isFullyCounted() {
        return items.stream().allMatch(i ->
            i.getStatus() == StockCountItem.Status.COUNTED ||
            i.getStatus() == StockCountItem.Status.VERIFIED);
    }
}
