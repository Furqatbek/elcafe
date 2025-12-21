package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Restaurant-level settings for inventory valuation method.
 * Controls how COGS and inventory value are calculated.
 */
@Entity
@Table(name = "valuation_settings", indexes = {
        @Index(name = "idx_valuation_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_valuation_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValuationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ValuationMethod valuationMethod = ValuationMethod.WEIGHTED_AVERAGE;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column
    private LocalDate effectiveTo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Check if this setting is currently effective
     */
    public boolean isCurrentlyEffective() {
        LocalDate today = LocalDate.now();
        boolean afterStart = !today.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || !today.isAfter(effectiveTo);
        return isActive && afterStart && beforeEnd;
    }

    /**
     * Deactivate this setting (when switching to a new method)
     */
    public void deactivate(LocalDate endDate) {
        this.isActive = false;
        this.effectiveTo = endDate;
    }
}
