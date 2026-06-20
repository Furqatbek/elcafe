package com.elcafe.modules.pos.shift.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "shift_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class ShiftRules {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    @Column(name = "max_shift_hours", nullable = false)
    @Builder.Default
    private Integer maxShiftHours = 8;

    @Column(name = "max_weekly_hours", nullable = false)
    @Builder.Default
    private Integer maxWeeklyHours = 40;

    @Column(name = "overtime_multiplier", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal overtimeMultiplier = new BigDecimal("1.50");

    @Column(name = "min_break_after_hours", nullable = false)
    @Builder.Default
    private Integer minBreakAfterHours = 4;

    @Column(name = "min_break_duration_minutes", nullable = false)
    @Builder.Default
    private Integer minBreakDurationMinutes = 30;

    @Column(name = "notify_overtime_at_hours", nullable = false)
    @Builder.Default
    private Integer notifyOvertimeAtHours = 7;

    @Column(name = "auto_clock_out_after_hours", nullable = false)
    @Builder.Default
    private Integer autoClockOutAfterHours = 12;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
