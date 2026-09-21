package com.elcafe.modules.sms.dto;

import com.elcafe.modules.sms.entity.SmsAutomationRule;
import com.elcafe.modules.sms.enums.AutomationTrigger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsAutomationRuleResponse {

    private Long id;
    private String name;
    private String description;
    private AutomationTrigger triggerType;
    private Long templateId;
    private String templateName;
    private Integer delayMinutes;
    private Boolean isActive;
    private Map<String, Object> conditions;
    private Integer sentCount;
    private LocalDateTime lastTriggeredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SmsAutomationRuleResponse from(SmsAutomationRule rule) {
        return SmsAutomationRuleResponse.builder()
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
