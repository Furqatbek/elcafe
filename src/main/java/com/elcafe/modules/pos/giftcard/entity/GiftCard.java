package com.elcafe.modules.pos.giftcard.entity;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.pos.giftcard.enums.GiftCardStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Individual gift card with balance tracking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gift_cards", indexes = {
    @Index(name = "idx_gift_cards_restaurant", columnList = "restaurant_id"),
    @Index(name = "idx_gift_cards_card_number", columnList = "card_number"),
    @Index(name = "idx_gift_cards_barcode", columnList = "barcode"),
    @Index(name = "idx_gift_cards_status", columnList = "status")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_gift_card_number", columnNames = {"restaurant_id", "card_number"})
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class GiftCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gift_card_type_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private GiftCardType giftCardType;

    @Column(name = "card_number", nullable = false, length = 50)
    private String cardNumber;

    @Column(name = "pin", length = 20)
    private String pin;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "initial_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal initialBalance;

    @Column(name = "current_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GiftCardStatus status = GiftCardStatus.ACTIVE;

    // Purchase info
    @Column(name = "purchased_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime purchasedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchased_by_customer_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer purchasedByCustomer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Order purchaseOrder;

    @Column(name = "purchase_amount", precision = 10, scale = 2)
    private BigDecimal purchaseAmount;

    // Recipient info
    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    @Column(name = "recipient_email", length = 200)
    private String recipientEmail;

    @Column(name = "recipient_phone", length = 50)
    private String recipientPhone;

    @Column(name = "personal_message", columnDefinition = "TEXT")
    private String personalMessage;

    // Validity
    @Column(name = "issued_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime expiresAt;

    @Column(name = "last_used_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastUsedAt;

    @OneToMany(mappedBy = "giftCard", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<GiftCardTransaction> transactions = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    // Helper methods

    public boolean isValid() {
        return status == GiftCardStatus.ACTIVE
            && currentBalance.compareTo(BigDecimal.ZERO) > 0
            && (expiresAt == null || expiresAt.isAfter(OffsetDateTime.now()));
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(OffsetDateTime.now());
    }

    public boolean canRedeem(BigDecimal amount) {
        return isValid() && currentBalance.compareTo(amount) >= 0;
    }

    @PrePersist
    private void onCreate() {
        if (issuedAt == null) {
            issuedAt = OffsetDateTime.now();
        }
        if (currentBalance == null) {
            currentBalance = initialBalance;
        }
    }

    @PreUpdate
    private void onUpdate() {
        // Auto-expire if past expiration date
        if (isExpired() && status == GiftCardStatus.ACTIVE) {
            status = GiftCardStatus.EXPIRED;
        }
        // Mark as redeemed if balance is zero
        if (currentBalance.compareTo(BigDecimal.ZERO) == 0 && status == GiftCardStatus.ACTIVE) {
            status = GiftCardStatus.REDEEMED;
        }
    }
}
