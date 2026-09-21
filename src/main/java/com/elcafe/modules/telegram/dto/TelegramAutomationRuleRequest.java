package com.elcafe.modules.telegram.dto;

import com.elcafe.modules.telegram.enums.TelegramTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramAutomationRuleRequest {

    @NotBlank(message = "Rule name is required")
    @Size(max = 100, message = "Rule name must be less than 100 characters")
    private String name;

    private String description;

    @NotNull(message = "Trigger type is required")
    private TelegramTriggerType triggerType;

    @NotNull(message = "Template ID is required")
    private Long templateId;

    private Integer delayMinutes;

    private Boolean isActive;

    private Map<String, Object> conditions;
}
