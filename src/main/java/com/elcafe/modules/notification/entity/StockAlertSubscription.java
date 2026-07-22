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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Entity for storing Telegram chat subscriptions for stock alerts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "stock_alert_subscriptions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "telegram_chat_id"}))
@EntityListeners(AuditingEntityListener.class)
public class StockAlertSubscription {

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

    @Column(name = "alert_on_low_stock", nullable = false)
    @Builder.Default
    private Boolean alertOnLowStock = true;

    @Column(name = "alert_on_reorder", nullable = false)
    @Builder.Default
    private Boolean alertOnReorder = true;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "last_alert_sent_at")
    private LocalDateTime lastAlertSentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
