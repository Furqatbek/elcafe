package com.elcafe.modules.telegram.entity;

import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.telegram.enums.TelegramMessageType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "telegram_logs")
@EntityListeners(AuditingEntityListener.class)
public class TelegramLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id")
    private TelegramSubscriber subscriber;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(length = 100)
    private String username;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 50)
    private TelegramMessageType messageType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private TelegramTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private TelegramCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "automation_rule_id")
    private TelegramAutomationRule automationRule;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

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
