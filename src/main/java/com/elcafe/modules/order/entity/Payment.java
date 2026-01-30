package com.elcafe.modules.order.entity;

import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
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

    private LocalDateTime paidAt;

    private LocalDateTime completedAt;

    private LocalDateTime refundedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

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
}
