package com.elcafe.modules.sms.entity;

import com.elcafe.modules.sms.enums.MessageStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "sms_campaign_recipients")
@EntityListeners(AuditingEntityListener.class)
// V165: SMS marketing data is per-tenant — scoped by the §3.4 restaurantFilter. (The Eskiz
// sending account itself stays platform-wide; see V165's header.)
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class SmsCampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant — always mirrors {@code campaign.restaurantId}. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private SmsCampaign campaign;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "message_content", columnDefinition = "TEXT")
    private String messageContent;

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

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void markAsSent(Long eskizMessageId) {
        this.status = MessageStatus.SENT;
        this.eskizMessageId = eskizMessageId;
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
