package com.elcafe.modules.telegram.dto;

import com.elcafe.modules.telegram.entity.TelegramAutomationRule;
import com.elcafe.modules.telegram.enums.TelegramTriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramAutomationRuleResponse {

    private Long id;
    private String name;
    private String description;
    private TelegramTriggerType triggerType;
    private Long templateId;
    private String templateName;
    private Integer delayMinutes;
    private Boolean isActive;
    private Map<String, Object> conditions;
    private Integer sentCount;
    private OffsetDateTime lastTriggeredAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static TelegramAutomationRuleResponse from(TelegramAutomationRule rule) {
        return TelegramAutomationRuleResponse.builder()
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
