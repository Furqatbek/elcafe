package com.elcafe.modules.reservation.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reservation_settings")
public class ReservationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "advance_days", nullable = false)
    @Builder.Default
    private Integer advanceDays = 30;

    @Column(name = "min_advance_hours", nullable = false)
    @Builder.Default
    private Integer minAdvanceHours = 2;

    @Column(name = "slot_duration_minutes", nullable = false)
    @Builder.Default
    private Integer slotDurationMinutes = 60;

    @Column(name = "min_party_size", nullable = false)
    @Builder.Default
    private Integer minPartySize = 1;

    @Column(name = "max_party_size", nullable = false)
    @Builder.Default
    private Integer maxPartySize = 20;

    @Column(name = "deposit_required", nullable = false)
    @Builder.Default
    private Boolean depositRequired = false;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "deposit_percent", precision = 5, scale = 2)
    private BigDecimal depositPercent;

    @Column(name = "cancellation_hours", nullable = false)
    @Builder.Default
    private Integer cancellationHours = 24;

    @Column(name = "auto_confirm", nullable = false)
    @Builder.Default
    private Boolean autoConfirm = false;

    @Column(name = "send_reminders", nullable = false)
    @Builder.Default
    private Boolean sendReminders = true;

    @Column(name = "reminder_hours_before", nullable = false)
    @Builder.Default
    private Integer reminderHoursBefore = 24;

    @Column(name = "max_reservations_per_slot")
    private Integer maxReservationsPerSlot;

    @Column(name = "notes_for_customers", columnDefinition = "TEXT")
    private String notesForCustomers;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
