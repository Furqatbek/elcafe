package com.elcafe.modules.waiter.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * KPI configuration for waiter performance targets.
 * Can be set at restaurant level (applies to all waiters) or per waiter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "waiter_kpi_configs")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class WaiterKPIConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiter_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Waiter waiter; // null means restaurant-wide default

    @Column(length = 100)
    private String name; // e.g., "Default KPI", "Peak Hours KPI"

    // Daily targets
    @Column(name = "target_orders_per_day")
    @Builder.Default
    private Integer targetOrdersPerDay = 20;

    @Column(name = "target_revenue_per_day", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal targetRevenuePerDay = BigDecimal.valueOf(500);

    @Column(name = "target_avg_ticket", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal targetAvgTicket = BigDecimal.valueOf(25);

    @Column(name = "target_tables_per_shift")
    @Builder.Default
    private Integer targetTablesPerShift = 10;

    // Service quality targets
    @Column(name = "target_avg_service_time_minutes")
    @Builder.Default
    private Integer targetAvgServiceTimeMinutes = 45; // from order to payment

    @Column(name = "max_complaint_rate_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal maxComplaintRatePercent = BigDecimal.valueOf(2.0);

    @Column(name = "min_customer_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal minCustomerRating = BigDecimal.valueOf(4.0); // out of 5

    // Upselling targets
    @Column(name = "target_upsell_rate_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal targetUpsellRatePercent = BigDecimal.valueOf(15.0);

    @Column(name = "target_dessert_attach_rate_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal targetDessertAttachRatePercent = BigDecimal.valueOf(20.0);

    @Column(name = "target_beverage_attach_rate_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal targetBeverageAttachRatePercent = BigDecimal.valueOf(60.0);

    // Bonus thresholds (percentage of target to earn bonus)
    @Column(name = "bonus_threshold_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal bonusThresholdPercent = BigDecimal.valueOf(100.0);

    @Column(name = "bonus_amount_per_threshold", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal bonusAmountPerThreshold = BigDecimal.valueOf(50.0);

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
