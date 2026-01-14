package com.elcafe.modules.notification.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Entity for storing Telegram chat subscriptions for daily financial reports
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "financial_alert_subscriptions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "telegram_chat_id"}))
@EntityListeners(AuditingEntityListener.class)
public class FinancialAlertSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "telegram_chat_id", nullable = false)
    private Long telegramChatId;

    @Column(name = "subscriber_name", length = 100)
    private String subscriberName;

    @Column(name = "alert_daily_revenue", nullable = false)
    @Builder.Default
    private Boolean alertDailyRevenue = true;

    @Column(name = "alert_daily_expenses", nullable = false)
    @Builder.Default
    private Boolean alertDailyExpenses = true;

    @Column(name = "alert_daily_profit", nullable = false)
    @Builder.Default
    private Boolean alertDailyProfit = true;

    @Column(name = "report_time")
    @Builder.Default
    private LocalTime reportTime = LocalTime.of(23, 0); // Default: 11 PM

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "last_report_sent_at")
    private LocalDateTime lastReportSentAt;

    @Column(name = "last_report_date")
    private java.time.LocalDate lastReportDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
