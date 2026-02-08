package com.elcafe.modules.referral.entity;

import com.elcafe.modules.customer.entity.Customer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "referral_codes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"code"}),
    @UniqueConstraint(columnNames = {"restaurant_id", "customer_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class ReferralCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 20, unique = true)
    private String code;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "referralCode", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @JsonIgnore
    private List<Referral> referrals = new ArrayList<>();

    /**
     * Check if the referral code is valid for use
     */
    public boolean isValid() {
        if (!Boolean.TRUE.equals(active)) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        if (maxUses != null && usageCount >= maxUses) {
            return false;
        }
        return true;
    }

    /**
     * Increment usage count
     */
    public void incrementUsage() {
        this.usageCount = (this.usageCount != null ? this.usageCount : 0) + 1;
    }
}
