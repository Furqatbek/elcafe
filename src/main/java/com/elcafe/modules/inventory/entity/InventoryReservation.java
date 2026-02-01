package com.elcafe.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tracks inventory reservations to prevent overselling.
 * When an order is being created, ingredients are reserved before checkout.
 * Reservations are either confirmed (converted to deductions) or expired (released).
 */
@Entity
@Table(name = "inventory_reservations", indexes = {
        @Index(name = "idx_reservation_ingredient", columnList = "ingredient_id"),
        @Index(name = "idx_reservation_order", columnList = "order_id"),
        @Index(name = "idx_reservation_session", columnList = "session_id"),
        @Index(name = "idx_reservation_status", columnList = "status"),
        @Index(name = "idx_reservation_expires", columnList = "expires_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Version
    private Long version;

    public enum ReservationStatus {
        PENDING,      // Reservation active, waiting for order confirmation
        CONFIRMED,    // Order confirmed, reservation converted to deduction
        RELEASED,     // Reservation released (cart abandoned or explicitly released)
        EXPIRED       // Reservation expired automatically
    }

    @PrePersist
    public void prePersist() {
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusMinutes(15);
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return status == ReservationStatus.PENDING && !isExpired();
    }
}
