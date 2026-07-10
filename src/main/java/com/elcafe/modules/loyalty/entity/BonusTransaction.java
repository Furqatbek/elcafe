package com.elcafe.modules.loyalty.entity;

import com.elcafe.modules.order.entity.Order;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "bonus_transactions", indexes = {
    @Index(name = "idx_bonus_transactions_customer_loyalty_id", columnList = "customer_loyalty_id"),
    @Index(name = "idx_bonus_transactions_order_id", columnList = "order_id"),
    @Index(name = "idx_bonus_transactions_created_at", columnList = "created_at"),
    @Index(name = "idx_bonus_transactions_idempotency_key", columnList = "idempotency_key")
})
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BonusTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_loyalty_id", nullable = false)
    private CustomerLoyalty customerLoyalty;

    // §3.7: owning tenant (NOT NULL since V153). Set from the parent loyalty row on create.
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    // Hibernate 6 native JSON binding. The old @Convert(JsonMapConverter) bound a VARCHAR param, which
    // Postgres rejects against a jsonb column (SQLSTATE 42804) — and the loyalty listener swallowed the
    // error, so points silently never accrued in prod. @JdbcTypeCode(JSON) binds the correct type
    // (verified: SmsCampaign uses the same pattern; H2 test mode accepts it too).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        EARNED,                 // Bonus earned from order
        SPENT,                  // Bonus used for payment
        REFUNDED,              // Bonus refunded due to order cancellation
        EXPIRED,               // Bonus expired
        ADJUSTMENT,            // Manual adjustment
        BIRTHDAY_BONUS,        // Birthday bonus
        FIRST_ORDER_BONUS,     // First order bonus
        REACTIVATION_BONUS,    // Reactivation bonus for inactive users
        PROMOTION_BONUS,       // Promotional bonus
        ADMIN_ADJUSTMENT,      // Admin manual adjustment
        REFERRAL_BONUS,        // Referral program bonus
        TOP_UP                 // Customer-funded wallet top-up (Click / Payme / cash-at-counter)
    }

    /**
     * Check if transaction is a credit (increases balance)
     */
    public boolean isCredit() {
        return transactionType == TransactionType.EARNED ||
               transactionType == TransactionType.REFUNDED ||
               transactionType == TransactionType.BIRTHDAY_BONUS ||
               transactionType == TransactionType.FIRST_ORDER_BONUS ||
               transactionType == TransactionType.REACTIVATION_BONUS ||
               transactionType == TransactionType.PROMOTION_BONUS ||
               transactionType == TransactionType.REFERRAL_BONUS ||
               transactionType == TransactionType.TOP_UP ||
               (transactionType == TransactionType.ADJUSTMENT && amount.compareTo(BigDecimal.ZERO) > 0) ||
               (transactionType == TransactionType.ADMIN_ADJUSTMENT && amount.compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * Check if transaction is a debit (decreases balance)
     */
    public boolean isDebit() {
        return transactionType == TransactionType.SPENT ||
               transactionType == TransactionType.EXPIRED ||
               (transactionType == TransactionType.ADJUSTMENT && amount.compareTo(BigDecimal.ZERO) < 0) ||
               (transactionType == TransactionType.ADMIN_ADJUSTMENT && amount.compareTo(BigDecimal.ZERO) < 0);
    }
}
