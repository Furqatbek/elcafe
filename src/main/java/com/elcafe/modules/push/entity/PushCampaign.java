package com.elcafe.modules.push.entity;

import com.elcafe.modules.push.enums.PushCampaignStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Push notification marketing campaigns.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "push_campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private PushTemplate template;

    @Column(name = "title")
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "icon", length = 500)
    private String icon;

    @Column(name = "image", length = 500)
    private String image;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actions", columnDefinition = "jsonb")
    private Map<String, Object> actions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    private Map<String, Object> data;

    @Column(name = "target_audience", nullable = false, length = 50)
    @Builder.Default
    private String targetAudience = "ALL";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_criteria", columnDefinition = "jsonb")
    private Map<String, Object> filterCriteria;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private PushCampaignStatus status = PushCampaignStatus.DRAFT;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "total_recipients")
    @Builder.Default
    private Integer totalRecipients = 0;

    @Column(name = "sent_count")
    @Builder.Default
    private Integer sentCount = 0;

    @Column(name = "delivered_count")
    @Builder.Default
    private Integer deliveredCount = 0;

    @Column(name = "clicked_count")
    @Builder.Default
    private Integer clickedCount = 0;

    @Column(name = "failed_count")
    @Builder.Default
    private Integer failedCount = 0;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Get effective title (from template or custom)
     */
    public String getEffectiveTitle() {
        if (title != null && !title.isEmpty()) {
            return title;
        }
        return template != null ? template.getTitle() : null;
    }

    /**
     * Get effective body (from template or custom)
     */
    public String getEffectiveBody() {
        if (body != null && !body.isEmpty()) {
            return body;
        }
        return template != null ? template.getBody() : null;
    }

    /**
     * Start the campaign
     */
    public void start() {
        this.status = PushCampaignStatus.SENDING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Complete the campaign
     */
    public void complete() {
        this.status = PushCampaignStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Increment sent counter
     */
    public void incrementSent() {
        this.sentCount++;
    }

    /**
     * Increment delivered counter
     */
    public void incrementDelivered() {
        this.deliveredCount++;
    }

    /**
     * Increment clicked counter
     */
    public void incrementClicked() {
        this.clickedCount++;
    }

    /**
     * Increment failed counter
     */
    public void incrementFailed() {
        this.failedCount++;
    }
}
