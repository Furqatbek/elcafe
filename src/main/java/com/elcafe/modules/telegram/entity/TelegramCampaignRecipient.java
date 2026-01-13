package com.elcafe.modules.telegram.entity;

import com.elcafe.modules.sms.enums.MessageStatus;
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
@Table(name = "telegram_campaign_recipients")
@EntityListeners(AuditingEntityListener.class)
public class TelegramCampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private TelegramCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private TelegramSubscriber subscriber;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "message_content", columnDefinition = "TEXT")
    private String messageContent;

    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private MessageStatus status = MessageStatus.PENDING;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void markAsSent(Long telegramMessageId) {
        this.status = MessageStatus.SENT;
        this.telegramMessageId = telegramMessageId;
        this.sentAt = LocalDateTime.now();
    }

    public void markAsDelivered() {
        this.status = MessageStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = MessageStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
