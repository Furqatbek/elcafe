package com.elcafe.modules.pos.shift.entity;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.entity.Waiter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "shift_schedules", indexes = {
        @Index(name = "idx_shift_schedule_restaurant", columnList = "restaurant_id, shift_date"),
        @Index(name = "idx_shift_schedule_employee", columnList = "employee_id, shift_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class ShiftSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "resetToken", "resetTokenExpiry"})
    private User employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "waiter_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Waiter waiter;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(length = 50)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Status {
        SCHEDULED,
        CONFIRMED,
        CANCELLED
    }

    /**
     * Overlap check that handles schedules crossing midnight. A schedule
     * whose endTime is at or before startTime is treated as ending on
     * the next calendar day, so a 22:00 → 02:00 row correctly conflicts
     * with another 01:00 → 05:00 row on the same shiftDate.
     *
     * Both arguments are normalized the same way (the caller may also
     * be supplying an overnight range), then both ranges are projected
     * onto a 0..48h axis and compared as ordinary intervals.
     */
    public boolean overlaps(LocalTime otherStart, LocalTime otherEnd) {
        long thisStart = startTime.toSecondOfDay();
        long thisEnd = endTime.toSecondOfDay();
        if (thisEnd <= thisStart) thisEnd += 24L * 3600L;

        long oStart = otherStart.toSecondOfDay();
        long oEnd = otherEnd.toSecondOfDay();
        if (oEnd <= oStart) oEnd += 24L * 3600L;

        return thisStart < oEnd && oStart < thisEnd;
    }
}
