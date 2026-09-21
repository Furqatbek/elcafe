package com.elcafe.modules.ownerbot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "owner_notification_settings")
@EntityListeners(AuditingEntityListener.class)
public class OwnerNotificationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private OwnerTelegramSubscriber subscriber;

    // Notification types
    @Column(name = "notify_new_order")
    @Builder.Default
    private Boolean notifyNewOrder = true;

    @Column(name = "notify_new_reservation")
    @Builder.Default
    private Boolean notifyNewReservation = true;

    @Column(name = "notify_low_stock")
    @Builder.Default
    private Boolean notifyLowStock = true;

    @Column(name = "notify_customer_review")
    @Builder.Default
    private Boolean notifyCustomerReview = true;

    @Column(name = "notify_daily_report")
    @Builder.Default
    private Boolean notifyDailyReport = true;

    @Column(name = "notify_critical_alerts")
    @Builder.Default
    private Boolean notifyCriticalAlerts = true;

    @Column(name = "notify_order_cancelled")
    @Builder.Default
    private Boolean notifyOrderCancelled = true;

    @Column(name = "notify_reservation_cancelled")
    @Builder.Default
    private Boolean notifyReservationCancelled = true;

    // Quiet hours
    @Column(name = "quiet_hours_enabled")
    @Builder.Default
    private Boolean quietHoursEnabled = false;

    @Column(name = "quiet_hours_start")
    @Builder.Default
    private LocalTime quietHoursStart = LocalTime.of(23, 0);

    @Column(name = "quiet_hours_end")
    @Builder.Default
    private LocalTime quietHoursEnd = LocalTime.of(7, 0);

    // Summary preferences
    @Column(name = "receive_hourly_summary")
    @Builder.Default
    private Boolean receiveHourlySummary = false;

    @Column(name = "receive_daily_summary")
    @Builder.Default
    private Boolean receiveDailySummary = true;

    @Column(name = "daily_summary_time")
    @Builder.Default
    private LocalTime dailySummaryTime = LocalTime.of(22, 0);

    // Thresholds
    @Column(name = "min_order_amount_notify")
    @Builder.Default
    private BigDecimal minOrderAmountNotify = BigDecimal.ZERO;

    @Column(name = "low_stock_threshold")
    @Builder.Default
    private Integer lowStockThreshold = 10;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isNotificationEnabled(String type) {
        return switch (type.toUpperCase()) {
            case "NEW_ORDER" -> Boolean.TRUE.equals(notifyNewOrder);
            case "NEW_RESERVATION" -> Boolean.TRUE.equals(notifyNewReservation);
            case "LOW_STOCK" -> Boolean.TRUE.equals(notifyLowStock);
            case "CUSTOMER_REVIEW", "REVIEW" -> Boolean.TRUE.equals(notifyCustomerReview);
            case "DAILY_REPORT" -> Boolean.TRUE.equals(notifyDailyReport);
            case "CRITICAL_ALERT" -> Boolean.TRUE.equals(notifyCriticalAlerts);
            case "ORDER_CANCELLED" -> Boolean.TRUE.equals(notifyOrderCancelled);
            case "RESERVATION_CANCELLED" -> Boolean.TRUE.equals(notifyReservationCancelled);
            default -> true;
        };
    }

    public boolean isInQuietHours() {
        if (!Boolean.TRUE.equals(quietHoursEnabled)) {
            return false;
        }
        LocalTime now = LocalTime.now();
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            // Same day quiet hours (e.g., 14:00 - 16:00)
            return !now.isBefore(quietHoursStart) && now.isBefore(quietHoursEnd);
        } else {
            // Overnight quiet hours (e.g., 23:00 - 07:00)
            return !now.isBefore(quietHoursStart) || now.isBefore(quietHoursEnd);
        }
    }
}
