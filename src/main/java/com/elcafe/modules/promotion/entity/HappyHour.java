package com.elcafe.modules.promotion.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "happy_hours")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HappyHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column
    @Builder.Default
    private Integer priority = 0;

    @OneToMany(mappedBy = "happyHour", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<HappyHourSchedule> schedules = new HashSet<>();

    @OneToMany(mappedBy = "happyHour", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<HappyHourProduct> products = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Check if this happy hour is currently active based on schedules
     */
    public boolean isCurrentlyActive() {
        if (!active) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        DayOfWeek today = now.getDayOfWeek();
        LocalTime currentTime = now.toLocalTime();

        return schedules.stream().anyMatch(schedule ->
            schedule.getDayOfWeek().equals(toDayString(today)) &&
            !currentTime.isBefore(schedule.getStartTime()) &&
            !currentTime.isAfter(schedule.getEndTime())
        );
    }

    /**
     * Check if this happy hour is active at a specific datetime
     */
    public boolean isActiveAt(LocalDateTime dateTime) {
        if (!active) {
            return false;
        }

        DayOfWeek dayOfWeek = dateTime.getDayOfWeek();
        LocalTime time = dateTime.toLocalTime();

        return schedules.stream().anyMatch(schedule ->
            schedule.getDayOfWeek().equals(toDayString(dayOfWeek)) &&
            !time.isBefore(schedule.getStartTime()) &&
            !time.isAfter(schedule.getEndTime())
        );
    }

    private String toDayString(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }

    // Helper methods for managing schedules
    public void addSchedule(HappyHourSchedule schedule) {
        schedules.add(schedule);
        schedule.setHappyHour(this);
    }

    public void removeSchedule(HappyHourSchedule schedule) {
        schedules.remove(schedule);
        schedule.setHappyHour(null);
    }

    public void clearSchedules() {
        schedules.forEach(s -> s.setHappyHour(null));
        schedules.clear();
    }

    // Helper methods for managing products
    public void addProduct(HappyHourProduct product) {
        products.add(product);
        product.setHappyHour(this);
    }

    public void removeProduct(HappyHourProduct product) {
        products.remove(product);
        product.setHappyHour(null);
    }

    public void clearProducts() {
        products.forEach(p -> p.setHappyHour(null));
        products.clear();
    }
}
