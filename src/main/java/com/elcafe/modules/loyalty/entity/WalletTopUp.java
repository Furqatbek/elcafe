package com.elcafe.modules.loyalty.entity;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.loyalty.converter.JsonMapConverter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Customer-initiated top-up of their loyalty wallet. Each row is a
 * payment intent that, on successful completion, credits the wallet
 * via a BonusTransaction with type TOP_UP.
 *
 * Top-ups are non-refundable promo credit (per product decision) — the
 * money becomes spendable bonus, not a stored-value liability.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "wallet_top_ups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTopUp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @Column(name = "external_transaction_id", length = 120)
    private String externalTransactionId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bonus_transaction_id")
    private BonusTransaction bonusTransaction;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime completedAt;

    public enum Status {
        PENDING,
        COMPLETED,
        FAILED,
        CANCELLED,
        EXPIRED;

        private static final Set<Status> TERMINAL = Set.of(COMPLETED, FAILED, CANCELLED, EXPIRED);

        public boolean isTerminal() {
            return TERMINAL.contains(this);
        }
    }

    public enum Provider {
        /** Click hosted-checkout — Uzbek card payment provider. */
        CLICK,
        /** Payme JSON-RPC merchant API — Uzbek card payment provider. */
        PAYME,
        /** Manual at-counter confirmation by an admin. */
        MANUAL
    }
}
