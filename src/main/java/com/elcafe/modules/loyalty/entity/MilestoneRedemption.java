package com.elcafe.modules.loyalty.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "milestone_redemptions", indexes = {
    @Index(name = "idx_milestone_redemptions_milestone_id", columnList = "milestone_id"),
    @Index(name = "idx_milestone_redemptions_customer_id", columnList = "customer_id"),
    @Index(name = "idx_milestone_redemptions_reward_pending", columnList = "reward_pending")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_milestone_customer", columnNames = {"milestone_id", "customer_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", nullable = false)
    private LoyaltyMilestone milestone;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Builder.Default
    @Column(name = "current_visits", nullable = false)
    private Integer currentVisits = 0;

    @Builder.Default
    @Column(name = "total_completions", nullable = false)
    private Integer totalCompletions = 0;

    @Builder.Default
    @Column(name = "reward_pending", nullable = false)
    private Boolean rewardPending = false;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_visit_order_id")
    private Order lastVisitOrder;

    @Column(name = "last_completion_at")
    private LocalDateTime lastCompletionAt;

    @Column(name = "last_redemption_at")
    private LocalDateTime lastRedemptionAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Record a visit and check if milestone is reached
     */
    public boolean recordVisit(Order order, int requiredVisits) {
        this.currentVisits++;
        this.lastVisitOrder = order;

        if (this.currentVisits >= requiredVisits) {
            this.rewardPending = true;
            this.totalCompletions++;
            this.lastCompletionAt = LocalDateTime.now();
            return true;
        }
        return false;
    }

    /**
     * Redeem the pending reward and reset counter if repeating
     */
    public void redeemReward(boolean isRepeating) {
        if (!this.rewardPending) {
            throw new IllegalStateException("No pending reward to redeem");
        }
        this.rewardPending = false;
        this.lastRedemptionAt = LocalDateTime.now();
        if (isRepeating) {
            this.currentVisits = 0;
        }
    }
}
