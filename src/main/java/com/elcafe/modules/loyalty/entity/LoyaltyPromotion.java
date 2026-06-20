package com.elcafe.modules.loyalty.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "loyalty_promotions", indexes = {
    @Index(name = "idx_loyalty_promotions_dates", columnList = "start_date,end_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class LoyaltyPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_type", nullable = false, length = 30)
    private PromotionType promotionType;

    @Column(name = "multiplier_value", precision = 3, scale = 2)
    private BigDecimal multiplierValue;

    @Column(name = "fixed_bonus_amount", precision = 10, scale = 2)
    private BigDecimal fixedBonusAmount;

    @Column(name = "start_date", nullable = false)
    private OffsetDateTime startDate;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Column(name = "days_of_week")
    private String daysOfWeek; // Comma-separated: "MONDAY,FRIDAY,SATURDAY"

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "max_bonus_per_order", precision = 10, scale = 2)
    private BigDecimal maxBonusPerOrder;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum PromotionType {
        BONUS_MULTIPLIER,    // Multiply bonus by value
        FIXED_BONUS,         // Add fixed bonus amount
        PERCENTAGE_BOOST     // Boost bonus percentage
    }

    /**
     * Check if promotion is currently active
     */
    public boolean isCurrentlyActive() {
        if (!active) return false;

        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(startDate)) return false;
        if (endDate != null && now.isAfter(endDate)) return false;

        // Check day of week if specified
        if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
            String currentDay = now.getDayOfWeek().name();
            return daysOfWeek.contains(currentDay);
        }

        return true;
    }

    /**
     * Check if order amount qualifies for promotion
     */
    public boolean isOrderQualified(BigDecimal orderAmount) {
        if (minOrderAmount == null) return true;
        return orderAmount.compareTo(minOrderAmount) >= 0;
    }
}
