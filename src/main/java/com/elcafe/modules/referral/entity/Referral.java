package com.elcafe.modules.referral.entity;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.referral.enums.ReferralStatus;
import com.elcafe.modules.referral.enums.RewardType;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "referrals", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"referee_id", "restaurant_id"})
})
@EntityListeners(AuditingEntityListener.class)
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_code_id", nullable = false)
    @ToString.Exclude
    private ReferralCode referralCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_id", nullable = false)
    private Customer referrer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referee_id", nullable = false)
    private Customer referee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReferralStatus status = ReferralStatus.PENDING;

    @Column(name = "referrer_reward_given")
    @Builder.Default
    private Boolean referrerRewardGiven = false;

    @Column(name = "referrer_reward_amount", precision = 10, scale = 2)
    private BigDecimal referrerRewardAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "referrer_reward_type")
    private RewardType referrerRewardType;

    @Column(name = "referee_reward_given")
    @Builder.Default
    private Boolean refereeRewardGiven = false;

    @Column(name = "referee_reward_amount", precision = 10, scale = 2)
    private BigDecimal refereeRewardAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "referee_reward_type")
    private RewardType refereeRewardType;

    @Column(name = "referrer_rewarded_at")
    private LocalDateTime referrerRewardedAt;

    @Column(name = "referee_rewarded_at")
    private LocalDateTime refereeRewardedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Mark referral as completed and record completion time
     */
    public void complete(Order completedOrder) {
        this.status = ReferralStatus.COMPLETED;
        this.order = completedOrder;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Grant referrer reward
     */
    public void grantReferrerReward(RewardType type, BigDecimal amount) {
        this.referrerRewardGiven = true;
        this.referrerRewardType = type;
        this.referrerRewardAmount = amount;
        this.referrerRewardedAt = LocalDateTime.now();
    }

    /**
     * Grant referee reward
     */
    public void grantRefereeReward(RewardType type, BigDecimal amount) {
        this.refereeRewardGiven = true;
        this.refereeRewardType = type;
        this.refereeRewardAmount = amount;
        this.refereeRewardedAt = LocalDateTime.now();
    }
}
