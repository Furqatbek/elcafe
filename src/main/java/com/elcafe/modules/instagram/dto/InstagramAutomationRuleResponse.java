package com.elcafe.modules.instagram.dto;

import com.elcafe.modules.instagram.entity.InstagramAutomationRule;
import com.elcafe.modules.instagram.enums.InstagramTriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Automation rule view for the operator. Mirrors {@code TelegramAutomationRuleResponse}. Deliberately
 * omits {@code restaurantId}, same rationale as {@code InstagramTemplateResponse}: every rule returned
 * by the service already belongs to the caller's own restaurant, so echoing the tenant id back is
 * redundant — leaving it off means this DTO can never be the thing that leaks another tenant's id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstagramAutomationRuleResponse {

    private Long id;
    private String name;
    private String description;
    private InstagramTriggerType triggerType;
    private Long templateId;
    private String templateName;
    private Integer delayMinutes;
    private Boolean isActive;
    private Map<String, Object> conditions;
    private Integer sentCount;
    private OffsetDateTime lastTriggeredAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static InstagramAutomationRuleResponse from(InstagramAutomationRule rule) {
        return InstagramAutomationRuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .description(rule.getDescription())
                .triggerType(rule.getTriggerType())
                .templateId(rule.getTemplate() != null ? rule.getTemplate().getId() : null)
                .templateName(rule.getTemplate() != null ? rule.getTemplate().getName() : null)
                .delayMinutes(rule.getDelayMinutes())
                .isActive(rule.getIsActive())
                .conditions(rule.getConditions())
                .sentCount(rule.getSentCount())
                .lastTriggeredAt(rule.getLastTriggeredAt())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
