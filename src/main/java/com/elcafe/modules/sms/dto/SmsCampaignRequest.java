package com.elcafe.modules.sms.dto;

import com.elcafe.modules.sms.enums.TargetAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class SmsCampaignRequest {

    @NotBlank(message = "Campaign name is required")
    @Size(max = 200, message = "Campaign name must be less than 200 characters")
    private String name;

    private String description;

    private Long templateId;

    private String customMessage;

    @NotNull(message = "Target audience is required")
    private TargetAudience targetAudience;

    private Long segmentId;

    private Map<String, Object> filterCriteria;

    private LocalDateTime scheduledAt;
}
