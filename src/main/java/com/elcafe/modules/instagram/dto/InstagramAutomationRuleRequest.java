package com.elcafe.modules.instagram.dto;

import com.elcafe.modules.instagram.enums.InstagramTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Create/update payload for an Instagram automation rule. Mirrors
 * {@code TelegramAutomationRuleRequest}, adapted to Instagram's per-tenant model: there is no
 * {@code restaurantId} field — the owning tenant is always resolved from the caller, never accepted
 * from the request body (same contract as {@code InstagramTemplateRequest}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstagramAutomationRuleRequest {

    @NotBlank(message = "Rule name is required")
    @Size(max = 100, message = "Rule name must be less than 100 characters")
    private String name;

    private String description;

    @NotNull(message = "Trigger type is required")
    private InstagramTriggerType triggerType;

    @NotNull(message = "Template ID is required")
    private Long templateId;

    /**
     * Reserved for parity with {@code TelegramAutomationRuleRequest.delayMinutes}. Must be 0 (or
     * omitted) — {@code InstagramAutomationService} rejects any positive value, since delayed delivery
     * is not implemented. See {@code InstagramAutomationRule.delayMinutes}'s javadoc.
     */
    private Integer delayMinutes;

    private Boolean isActive;

    private Map<String, Object> conditions;
}
