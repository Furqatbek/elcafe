package com.elcafe.modules.loyalty.entity;

import com.elcafe.modules.customer.entity.Customer;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_loyalty")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLoyalty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(name = "current_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "lifetime_earned", nullable = false, precision = 10, scale = 2)
    private BigDecimal lifetimeEarned = BigDecimal.ZERO;

    @Column(name = "lifetime_spent", nullable = false, precision = 10, scale = 2)
    private BigDecimal lifetimeSpent = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id")
    private CustomerTier tier;

    @Column(name = "total_spent", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "order_count", nullable = false)
    private Integer orderCount = 0;

    @Column(name = "last_order_date")
    private LocalDateTime lastOrderDate;

    @Column(name = "birthday_bonus_claimed_year")
    private Integer birthdayBonusClaimedYear;

    @Column(name = "first_order_bonus_claimed")
    private Boolean firstOrderBonusClaimed = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Add bonus to current balance
     */
    public void addBonus(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Bonus amount must be positive");
        }
        this.currentBalance = this.currentBalance.add(amount);
        this.lifetimeEarned = this.lifetimeEarned.add(amount);
    }

    /**
     * Deduct bonus from current balance
     */
    public void deductBonus(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deduction amount must be positive");
        }
        if (this.currentBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient bonus balance");
        }
        this.currentBalance = this.currentBalance.subtract(amount);
        this.lifetimeSpent = this.lifetimeSpent.add(amount);
    }

    /**
     * Check if customer has enough bonus balance
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.currentBalance.compareTo(amount) >= 0;
    }
}
