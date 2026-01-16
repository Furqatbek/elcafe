package com.elcafe.modules.telegram.entity;

import com.elcafe.modules.telegram.enums.TelegramTriggerType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "telegram_automation_rules")
@EntityListeners(AuditingEntityListener.class)
public class TelegramAutomationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private TelegramTriggerType triggerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private TelegramTemplate template;

    @Column(name = "delay_minutes")
    @Builder.Default
    private Integer delayMinutes = 0;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> conditions;

    @Column(name = "sent_count")
    @Builder.Default
    private Integer sentCount = 0;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void incrementSentCount() {
        this.sentCount = (this.sentCount != null ? this.sentCount : 0) + 1;
        this.lastTriggeredAt = LocalDateTime.now();
    }

    public boolean shouldTrigger(Map<String, Object> context) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        // Check days_inactive condition
        if (conditions.containsKey("days_inactive") && context.containsKey("days_inactive")) {
            Object requiredObj = conditions.get("days_inactive");
            Object actualObj = context.get("days_inactive");
            if (requiredObj != null && actualObj != null) {
                int requiredDays = requiredObj instanceof Number ? ((Number) requiredObj).intValue() : 0;
                int actualDays = actualObj instanceof Number ? ((Number) actualObj).intValue() : 0;
                if (actualDays < requiredDays) {
                    return false;
                }
            }
        }

        return true;
    }
}
