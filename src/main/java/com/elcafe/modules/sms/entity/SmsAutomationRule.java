package com.elcafe.modules.sms.entity;

import com.elcafe.modules.sms.enums.AutomationTrigger;
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
@Table(name = "sms_automation_rules")
@EntityListeners(AuditingEntityListener.class)
public class SmsAutomationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private AutomationTrigger triggerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private SmsTemplate template;

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
        this.sentCount = (this.sentCount == null ? 0 : this.sentCount) + 1;
        this.lastTriggeredAt = LocalDateTime.now();
    }

    /**
     * Check if this rule should be triggered based on conditions
     */
    public boolean shouldTrigger(Map<String, Object> context) {
        if (!Boolean.TRUE.equals(isActive)) {
            return false;
        }

        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        // Check days_inactive condition
        if (conditions.containsKey("days_inactive")) {
            Integer requiredDays = (Integer) conditions.get("days_inactive");
            Integer actualDays = (Integer) context.get("days_inactive");
            if (actualDays == null || actualDays < requiredDays) {
                return false;
            }
        }

        // Check min_orders condition
        if (conditions.containsKey("min_orders")) {
            Integer requiredOrders = (Integer) conditions.get("min_orders");
            Integer actualOrders = (Integer) context.get("order_count");
            if (actualOrders == null || actualOrders < requiredOrders) {
                return false;
            }
        }

        // Check loyalty_tier condition
        if (conditions.containsKey("loyalty_tier")) {
            String requiredTier = (String) conditions.get("loyalty_tier");
            String actualTier = (String) context.get("loyalty_tier");
            if (actualTier == null || !actualTier.equals(requiredTier)) {
                return false;
            }
        }

        return true;
    }
}
