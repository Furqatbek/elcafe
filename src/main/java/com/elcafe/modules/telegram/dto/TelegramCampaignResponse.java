package com.elcafe.modules.telegram.dto;

import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.telegram.entity.TelegramCampaign;
import com.elcafe.modules.telegram.enums.TelegramTargetAudience;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramCampaignResponse {

    private Long id;
    private String name;
    private String description;
    private Long templateId;
    private String templateName;
    private String customMessage;
    private String imageUrl;
    private List<Map<String, String>> buttonsConfig;
    private TelegramTargetAudience targetAudience;
    private Map<String, Object> filterCriteria;
    private Integer recipientCount;
    private Integer sentCount;
    private Integer deliveredCount;
    private Integer failedCount;
    private Double deliveryRate;
    private CampaignStatus status;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TelegramCampaignResponse from(TelegramCampaign campaign) {
        TelegramCampaignResponseBuilder builder = TelegramCampaignResponse.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .description(campaign.getDescription())
                .customMessage(campaign.getCustomMessage())
                .imageUrl(campaign.getImageUrl())
                .buttonsConfig(campaign.getButtonsConfig())
                .targetAudience(campaign.getTargetAudience())
                .filterCriteria(campaign.getFilterCriteria())
                .recipientCount(campaign.getRecipientCount())
                .sentCount(campaign.getSentCount())
                .deliveredCount(campaign.getDeliveredCount())
                .failedCount(campaign.getFailedCount())
                .deliveryRate(campaign.getDeliveryRate())
                .status(campaign.getStatus())
                .scheduledAt(campaign.getScheduledAt())
                .startedAt(campaign.getStartedAt())
                .completedAt(campaign.getCompletedAt())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt());

        if (campaign.getTemplate() != null) {
            builder.templateId(campaign.getTemplate().getId())
                   .templateName(campaign.getTemplate().getName());
        }

        return builder.build();
    }
}
