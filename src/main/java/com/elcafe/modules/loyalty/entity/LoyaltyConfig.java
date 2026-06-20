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
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "loyalty_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class LoyaltyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "bonus_rate_type", nullable = false, length = 20)
    private BonusRateType bonusRateType = BonusRateType.PERCENTAGE;

    @Builder.Default
    @Column(name = "bonus_rate_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal bonusRateValue = new BigDecimal("5.0");

    @Builder.Default
    @Column(name = "max_bonus_payment_percentage", nullable = false)
    private Integer maxBonusPaymentPercentage = 50;

    @Builder.Default
    @Column(name = "min_order_amount_for_bonus", precision = 10, scale = 2)
    private BigDecimal minOrderAmountForBonus = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "birthday_bonus_amount", precision = 10, scale = 2)
    private BigDecimal birthdayBonusAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "first_order_bonus_amount", precision = 10, scale = 2)
    private BigDecimal firstOrderBonusAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "reactivation_bonus_amount", precision = 10, scale = 2)
    private BigDecimal reactivationBonusAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "reactivation_days_threshold")
    private Integer reactivationDaysThreshold = 30;

    @Column(name = "bonus_expiry_days")
    private Integer bonusExpiryDays;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum BonusRateType {
        PERCENTAGE,
        FIXED_AMOUNT
    }
}
