package com.elcafe.modules.bundle.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bundles")
@EntityListeners(AuditingEntityListener.class)
public class Bundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "bundle_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal bundlePrice;

    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "savings_amount", precision = 10, scale = 2)
    private BigDecimal savingsAmount;

    @Column(name = "savings_percent", precision = 5, scale = 2)
    private BigDecimal savingsPercent;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "available_from")
    private LocalTime availableFrom;

    @Column(name = "available_until")
    private LocalTime availableUntil;

    @Column(name = "available_days", length = 50)
    private String availableDays;

    @Column(name = "max_per_order")
    private Integer maxPerOrder;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "bundle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private Set<BundleItem> items = new HashSet<>();

    @OneToMany(mappedBy = "bundle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private Set<BundleOptionGroup> optionGroups = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper methods
    public void addItem(BundleItem item) {
        items.add(item);
        item.setBundle(this);
    }

    public void removeItem(BundleItem item) {
        items.remove(item);
        item.setBundle(null);
    }

    public void addOptionGroup(BundleOptionGroup group) {
        optionGroups.add(group);
        group.setBundle(this);
    }

    public void removeOptionGroup(BundleOptionGroup group) {
        optionGroups.remove(group);
        group.setBundle(null);
    }

    /**
     * Calculate and update savings based on original vs bundle price
     */
    public void calculateSavings() {
        if (originalPrice != null && bundlePrice != null && originalPrice.compareTo(BigDecimal.ZERO) > 0) {
            this.savingsAmount = originalPrice.subtract(bundlePrice);
            this.savingsPercent = savingsAmount
                    .multiply(BigDecimal.valueOf(100))
                    .divide(originalPrice, 2, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * Check if bundle is currently available based on time restrictions
     */
    public boolean isCurrentlyAvailable() {
        if (!Boolean.TRUE.equals(active)) {
            return false;
        }

        LocalTime now = LocalTime.now();

        // Check time restrictions
        if (availableFrom != null && availableUntil != null) {
            if (availableFrom.isBefore(availableUntil)) {
                // Normal time range (e.g., 11:00 - 14:00)
                if (now.isBefore(availableFrom) || now.isAfter(availableUntil)) {
                    return false;
                }
            } else {
                // Overnight range (e.g., 22:00 - 02:00)
                if (now.isBefore(availableFrom) && now.isAfter(availableUntil)) {
                    return false;
                }
            }
        }

        // Check day restrictions
        if (availableDays != null && !availableDays.isEmpty()) {
            String today = java.time.LocalDate.now().getDayOfWeek().name().substring(0, 3);
            if (!availableDays.toUpperCase().contains(today)) {
                return false;
            }
        }

        return true;
    }
}
