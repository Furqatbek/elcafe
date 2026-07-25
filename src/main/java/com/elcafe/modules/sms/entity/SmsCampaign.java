package com.elcafe.modules.sms.entity;

import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.enums.TargetAudience;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "sms_campaigns")
@EntityListeners(AuditingEntityListener.class)
// V165: SMS marketing data is per-tenant — scoped by the §3.4 restaurantFilter. (The Eskiz
// sending account itself stays platform-wide; see V165's header.)
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class SmsCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant — a campaign may only target this restaurant's customers. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private SmsTemplate template;

    @Column(name = "custom_message", columnDefinition = "TEXT")
    private String customMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_audience", nullable = false, length = 50)
    private TargetAudience targetAudience;

    @Column(name = "segment_id")
    private Long segmentId;

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

    @Column(name = "total_cost", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;

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

    public void incrementSentCount() {
        this.sentCount = (this.sentCount == null ? 0 : this.sentCount) + 1;
    }

    public void incrementDeliveredCount() {
        this.deliveredCount = (this.deliveredCount == null ? 0 : this.deliveredCount) + 1;
    }

    public void incrementFailedCount() {
        this.failedCount = (this.failedCount == null ? 0 : this.failedCount) + 1;
    }

    public void addCost(BigDecimal cost) {
        if (cost != null) {
            this.totalCost = (this.totalCost == null ? BigDecimal.ZERO : this.totalCost).add(cost);
        }
    }

    public double getDeliveryRate() {
        if (sentCount == null || sentCount == 0) return 0;
        return (deliveredCount != null ? deliveredCount : 0) * 100.0 / sentCount;
    }

    public String getMessage() {
        if (customMessage != null && !customMessage.isEmpty()) {
            return customMessage;
        }
        return template != null ? template.getContent() : null;
    }
}
