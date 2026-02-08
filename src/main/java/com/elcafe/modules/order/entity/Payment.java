package com.elcafe.modules.order.entity;

import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_order", columnList = "order_id"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_payment_created_at", columnList = "created_at"),
        @Index(name = "idx_payment_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "tip_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal tipAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "amount_tendered", precision = 10, scale = 2)
    private BigDecimal amountTendered;

    @Column(name = "change_due", precision = 10, scale = 2)
    private BigDecimal changeDue;

    @Column(length = 200)
    private String transactionId;

    @Column(length = 200)
    private String paymentGateway;

    @Column(columnDefinition = "TEXT")
    private String paymentDetails;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "processed_by", length = 100)
    private String processedBy;

    @Column(name = "split_number")
    private Integer splitNumber;

    @Column(name = "paid_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime paidAt;

    @Column(name = "completed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime completedAt;

    @Column(name = "refunded_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime refundedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    // Soft delete support - financial records should never be hard deleted
    @Column(name = "deleted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    /**
     * Version field for optimistic locking.
     * Prevents race conditions during concurrent payment operations
     * (e.g., split payments, refunds, status updates).
     */
    @Version
    private Long version;

    /**
     * Get total payment including tip
     */
    public BigDecimal getTotalWithTip() {
        BigDecimal tip = tipAmount != null ? tipAmount : BigDecimal.ZERO;
        return amount.add(tip);
    }

    /**
     * Get net amount after refunds
     */
    public BigDecimal getNetAmount() {
        BigDecimal refunded = refundedAmount != null ? refundedAmount : BigDecimal.ZERO;
        return getTotalWithTip().subtract(refunded);
    }

    // ==================== SOFT DELETE METHODS ====================

    /**
     * Check if this payment has been soft-deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Soft delete this payment. Financial records should never be hard deleted.
     */
    public void softDelete(String deletedByUser) {
        this.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.deletedBy = deletedByUser;
    }

    /**
     * Restore a soft-deleted payment.
     */
    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }
}
