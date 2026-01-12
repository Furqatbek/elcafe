package com.elcafe.modules.referral.entity;

import com.elcafe.modules.referral.enums.RewardType;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "referral_settings")
@EntityListeners(AuditingEntityListener.class)
public class ReferralSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    @Column(name = "program_active")
    @Builder.Default
    private Boolean programActive = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "referrer_reward_type", nullable = false)
    @Builder.Default
    private RewardType referrerRewardType = RewardType.BONUS_POINTS;

    @Column(name = "referrer_reward_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal referrerRewardAmount = BigDecimal.valueOf(100);

    @Enumerated(EnumType.STRING)
    @Column(name = "referee_reward_type", nullable = false)
    @Builder.Default
    private RewardType refereeRewardType = RewardType.BONUS_POINTS;

    @Column(name = "referee_reward_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal refereeRewardAmount = BigDecimal.valueOf(50);

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "max_referrals_per_customer")
    private Integer maxReferralsPerCustomer;

    @Column(name = "reward_expires_days")
    @Builder.Default
    private Integer rewardExpiresDays = 30;

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
