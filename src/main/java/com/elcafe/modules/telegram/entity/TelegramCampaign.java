package com.elcafe.modules.telegram.entity;

import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.telegram.enums.TelegramTargetAudience;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "telegram_campaigns")
@EntityListeners(AuditingEntityListener.class)
public class TelegramCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private TelegramTemplate template;

    @Column(name = "custom_message", columnDefinition = "TEXT")
    private String customMessage;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "buttons_config", columnDefinition = "jsonb")
    private List<Map<String, String>> buttonsConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_audience", nullable = false, length = 50)
    private TelegramTargetAudience targetAudience;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_criteria", columnDefinition = "jsonb")
    private Map<String, Object> filterCriteria;

    @Column(name = "recipient_count")
    @Builder.Default
    private Integer recipientCount = 0;

    @Column(name = "sent_count")
    @Builder.Default
    private Integer sentCount = 0;

    @Column(name = "delivered_count")
    @Builder.Default
    private Integer deliveredCount = 0;

    @Column(name = "failed_count")
    @Builder.Default
    private Integer failedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public String getMessage() {
        if (customMessage != null && !customMessage.isEmpty()) {
            return customMessage;
        }
        return template != null ? template.getContent() : "";
    }

    public void incrementSentCount() {
        this.sentCount = (this.sentCount != null ? this.sentCount : 0) + 1;
    }

    public void incrementFailedCount() {
        this.failedCount = (this.failedCount != null ? this.failedCount : 0) + 1;
    }

    public void incrementDeliveredCount() {
        this.deliveredCount = (this.deliveredCount != null ? this.deliveredCount : 0) + 1;
    }

    public Double getDeliveryRate() {
        if (sentCount == null || sentCount == 0) return 0.0;
        int delivered = deliveredCount != null ? deliveredCount : 0;
        return (delivered * 100.0) / sentCount;
    }
}
