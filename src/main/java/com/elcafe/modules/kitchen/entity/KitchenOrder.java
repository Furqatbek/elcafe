package com.elcafe.modules.kitchen.entity;

import com.elcafe.modules.kitchen.enums.KitchenOrderStatus;
import com.elcafe.modules.kitchen.enums.KitchenPriority;
import com.elcafe.modules.order.entity.Order;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "kitchen_orders")
@EntityListeners(AuditingEntityListener.class)
public class KitchenOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private KitchenOrderStatus status = KitchenOrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private KitchenPriority priority = KitchenPriority.NORMAL;

    @Column(name = "assigned_chef")
    private String assignedChef;

    @Column(name = "preparation_started_at")
    private LocalDateTime preparationStartedAt;

    @Column(name = "preparation_completed_at")
    private LocalDateTime preparationCompletedAt;

    @Column(name = "estimated_preparation_time_minutes")
    private Integer estimatedPreparationTimeMinutes;

    @Column(name = "actual_preparation_time_minutes")
    private Integer actualPreparationTimeMinutes;

    @Column(length = 1000)
    private String notes;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void startPreparation(String chefName) {
        this.status = KitchenOrderStatus.PREPARING;
        this.assignedChef = chefName;
        this.preparationStartedAt = LocalDateTime.now();
    }

    public void completePreparation() {
        this.status = KitchenOrderStatus.READY;
        this.preparationCompletedAt = LocalDateTime.now();

        if (this.preparationStartedAt != null) {
            long minutesTaken = java.time.Duration.between(
                    this.preparationStartedAt,
                    this.preparationCompletedAt
            ).toMinutes();
            this.actualPreparationTimeMinutes = (int) minutesTaken;
        }
    }
}
