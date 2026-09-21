package com.elcafe.modules.telegram.dto;

import com.elcafe.modules.telegram.enums.TelegramTargetAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramCampaignRequest {

    @NotBlank(message = "Campaign name is required")
    @Size(max = 200, message = "Campaign name must be less than 200 characters")
    private String name;

    private String description;

    private Long templateId;

    private String customMessage;

    private String imageUrl;

    private List<Map<String, String>> buttonsConfig;

    @NotNull(message = "Target audience is required")
    private TelegramTargetAudience targetAudience;

    private Map<String, Object> filterCriteria;

    private OffsetDateTime scheduledAt;
}
