package com.elcafe.modules.waiter.entity;

import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.enums.CommissionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tracks commission earned by a waiter from a specific order
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "waiter_commissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"waiter_id", "order_id"}))
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class WaiterCommission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiter_id", nullable = false)
    private Waiter waiter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    /**
     * The order total used for commission calculation
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal orderTotal;

    /**
     * The commission percentage applied (snapshot at time of calculation)
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    /**
     * The calculated commission amount
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    /**
     * Status of the commission
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CommissionStatus status = CommissionStatus.PENDING;

    /**
     * Reference to payroll entry when commission is processed
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_entry_id")
    private PayrollEntry payrollEntry;

    /**
     * When the commission was paid
     */
    private LocalDateTime paidAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Mark commission as paid
     */
    public void markAsPaid() {
        this.status = CommissionStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    /**
     * Mark commission as cancelled
     */
    public void cancel() {
        this.status = CommissionStatus.CANCELLED;
    }
}
