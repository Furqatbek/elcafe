package com.elcafe.modules.push.entity;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.push.enums.PushNotificationStatus;
import com.elcafe.modules.push.enums.PushNotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Logs all sent push notifications with delivery status.
 */
@Entity
@Table(name = "push_notification_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private PushSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "icon", length = 500)
    private String icon;

    @Column(name = "image", length = 500)
    private String image;

    @Column(name = "badge", length = 500)
    private String badge;

    @Column(name = "tag", length = 100)
    private String tag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    private Map<String, Object> data;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actions", columnDefinition = "jsonb")
    private Map<String, Object> actions;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private PushNotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private PushNotificationStatus status = PushNotificationStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "automation_rule_id")
    private Long automationRuleId;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Mark notification as sent
     */
    public void markAsSent() {
        this.status = PushNotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * Mark notification as delivered
     */
    public void markAsDelivered() {
        this.status = PushNotificationStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    /**
     * Mark notification as clicked
     */
    public void markAsClicked() {
        this.status = PushNotificationStatus.CLICKED;
        this.clickedAt = LocalDateTime.now();
    }

    /**
     * Mark notification as failed
     */
    public void markAsFailed(String errorMessage) {
        this.status = PushNotificationStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
