package com.elcafe.modules.sms.entity;

import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.sms.enums.SmsMessageType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "sms_logs")
@EntityListeners(AuditingEntityListener.class)
public class SmsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 50)
    private SmsMessageType messageType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private SmsTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private SmsCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "automation_rule_id")
    private SmsAutomationRule automationRule;

    @Column(name = "eskiz_message_id")
    private Long eskizMessageId;

    @Column(name = "eskiz_dispatch_id")
    private Long eskizDispatchId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private MessageStatus status = MessageStatus.PENDING;

    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "status_updated_at")
    private LocalDateTime statusUpdatedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void markAsSent(Long eskizMessageId) {
        this.status = MessageStatus.SENT;
        this.eskizMessageId = eskizMessageId;
        this.sentAt = LocalDateTime.now();
        this.statusUpdatedAt = LocalDateTime.now();
    }

    public void markAsDelivered() {
        this.status = MessageStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
        this.statusUpdatedAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = MessageStatus.FAILED;
        this.errorMessage = errorMessage;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    public void updateStatus(MessageStatus newStatus) {
        this.status = newStatus;
        this.statusUpdatedAt = LocalDateTime.now();
        if (newStatus == MessageStatus.DELIVERED) {
            this.deliveredAt = LocalDateTime.now();
        }
    }
}
