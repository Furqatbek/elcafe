package com.elcafe.modules.reservation.entity;

import com.elcafe.modules.auth.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.reservation.enums.ReservationSource;
import com.elcafe.modules.reservation.enums.ReservationStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    @ToString.Exclude
    private RestaurantTable table;

    // Customer details (for walk-ins or guests without account)
    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    // Reservation details
    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    @Column(name = "reservation_time", nullable = false)
    private LocalTime reservationTime;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private Integer durationMinutes = 60;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    // Special requests
    @Column(name = "special_requests", columnDefinition = "TEXT")
    private String specialRequests;

    @Column(length = 50)
    private String occasion;

    // Confirmation
    @Column(name = "confirmation_code", nullable = false, unique = true, length = 20)
    private String confirmationCode;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    // Check-in/completion
    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Cancellation
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "no_show")
    @Builder.Default
    private Boolean noShow = false;

    // Deposit info
    @Column(name = "deposit_required", nullable = false)
    @Builder.Default
    private Boolean depositRequired = false;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "deposit_paid", nullable = false)
    @Builder.Default
    private Boolean depositPaid = false;

    // Source
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @Builder.Default
    private ReservationSource source = ReservationSource.WEBSITE;

    // Reminders
    @Column(name = "reminder_sent", nullable = false)
    @Builder.Default
    private Boolean reminderSent = false;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @JsonIgnore
    private List<ReservationDeposit> deposits = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Get the end time of the reservation
     */
    public LocalTime getEndTime() {
        return reservationTime.plusMinutes(durationMinutes);
    }

    /**
     * Check if reservation can be cancelled
     */
    public boolean canBeCancelled() {
        return status == ReservationStatus.PENDING ||
               status == ReservationStatus.CONFIRMED ||
               status == ReservationStatus.DEPOSIT_PENDING;
    }

    /**
     * Check if reservation is upcoming (not yet seated or completed)
     */
    public boolean isUpcoming() {
        return status == ReservationStatus.PENDING ||
               status == ReservationStatus.CONFIRMED ||
               status == ReservationStatus.DEPOSIT_PENDING;
    }
}
