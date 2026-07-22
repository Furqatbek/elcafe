package com.elcafe.modules.waiter.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily performance record for a waiter.
 * Tracks all KPI metrics for performance evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "waiter_performance",
       uniqueConstraints = @UniqueConstraint(columnNames = {"waiter_id", "performance_date"}))
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class WaiterPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiter_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Waiter waiter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Restaurant restaurant;

    @Column(name = "performance_date", nullable = false)
    private LocalDate performanceDate;

    // Order metrics
    @Column(name = "total_orders")
    @Builder.Default
    private Integer totalOrders = 0;

    @Column(name = "total_tables_served")
    @Builder.Default
    private Integer totalTablesServed = 0;

    @Column(name = "total_customers_served")
    @Builder.Default
    private Integer totalCustomersServed = 0;

    // Revenue metrics
    @Column(name = "total_revenue", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_tips", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalTips = BigDecimal.ZERO;

    @Column(name = "avg_ticket_value", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal avgTicketValue = BigDecimal.ZERO;

    // Service time metrics (in minutes)
    @Column(name = "avg_service_time_minutes")
    @Builder.Default
    private Integer avgServiceTimeMinutes = 0;

    @Column(name = "min_service_time_minutes")
    private Integer minServiceTimeMinutes;

    @Column(name = "max_service_time_minutes")
    private Integer maxServiceTimeMinutes;

    // Quality metrics
    @Column(name = "complaints_count")
    @Builder.Default
    private Integer complaintsCount = 0;

    @Column(name = "compliments_count")
    @Builder.Default
    private Integer complimentsCount = 0;

    @Column(name = "avg_customer_rating", precision = 3, scale = 2)
    private BigDecimal avgCustomerRating;

    @Column(name = "ratings_count")
    @Builder.Default
    private Integer ratingsCount = 0;

    // Upselling metrics
    @Column(name = "upsell_attempts")
    @Builder.Default
    private Integer upsellAttempts = 0;

    @Column(name = "upsell_successes")
    @Builder.Default
    private Integer upsellSuccesses = 0;

    @Column(name = "dessert_orders")
    @Builder.Default
    private Integer dessertOrders = 0;

    @Column(name = "beverage_orders")
    @Builder.Default
    private Integer beverageOrders = 0;

    // Void/discount metrics
    @Column(name = "void_items_count")
    @Builder.Default
    private Integer voidItemsCount = 0;

    @Column(name = "void_items_value", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal voidItemsValue = BigDecimal.ZERO;

    @Column(name = "discounts_given")
    @Builder.Default
    private Integer discountsGiven = 0;

    @Column(name = "discounts_value", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountsValue = BigDecimal.ZERO;

    // Shift info
    @Column(name = "shift_start")
    private LocalDateTime shiftStart;

    @Column(name = "shift_end")
    private LocalDateTime shiftEnd;

    @Column(name = "hours_worked", precision = 4, scale = 2)
    private BigDecimal hoursWorked;

    // KPI achievement (calculated)
    @Column(name = "kpi_score", precision = 5, scale = 2)
    private BigDecimal kpiScore; // percentage of KPI achievement

    @Column(name = "bonus_earned", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal bonusEarned = BigDecimal.ZERO;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Helper methods
    public BigDecimal getUpsellRate() {
        if (upsellAttempts == null || upsellAttempts == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(upsellSuccesses)
                .divide(BigDecimal.valueOf(upsellAttempts), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    public BigDecimal getComplaintRate() {
        if (totalOrders == null || totalOrders == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(complaintsCount)
                .divide(BigDecimal.valueOf(totalOrders), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    public BigDecimal getDessertAttachRate() {
        if (totalOrders == null || totalOrders == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(dessertOrders)
                .divide(BigDecimal.valueOf(totalOrders), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    public BigDecimal getBeverageAttachRate() {
        if (totalOrders == null || totalOrders == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(beverageOrders)
                .divide(BigDecimal.valueOf(totalOrders), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
