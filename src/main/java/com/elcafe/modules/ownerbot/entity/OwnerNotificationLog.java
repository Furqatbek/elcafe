package com.elcafe.modules.ownerbot.entity;

import com.elcafe.modules.ownerbot.enums.OwnerNotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "owner_notification_log")
@EntityListeners(AuditingEntityListener.class)
public class OwnerNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id")
    private OwnerTelegramSubscriber subscriber;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private OwnerNotificationType notificationType;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType; // ORDER, RESERVATION, INVENTORY, REVIEW

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "telegram_message_id")
    private Integer telegramMessageId;

    @Column(length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, SENT, DELIVERED, FAILED

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void markSent(Integer messageId) {
        this.status = "SENT";
        this.telegramMessageId = messageId;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.status = "FAILED";
        this.errorMessage = error;
    }
}
