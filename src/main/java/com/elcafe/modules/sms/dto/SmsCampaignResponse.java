package com.elcafe.modules.sms.dto;

import com.elcafe.modules.sms.entity.SmsCampaign;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.enums.TargetAudience;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsCampaignResponse {

    private Long id;
    private String name;
    private String description;
    private Long templateId;
    private String templateName;
    private String customMessage;
    private TargetAudience targetAudience;
    private Long segmentId;
    private Map<String, Object> filterCriteria;
    private Integer recipientCount;
    private Integer sentCount;
    private Integer deliveredCount;
    private Integer failedCount;
    private BigDecimal totalCost;
    private CampaignStatus status;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private Double deliveryRate;

    public static SmsCampaignResponse from(SmsCampaign campaign) {
        SmsCampaignResponseBuilder builder = SmsCampaignResponse.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .description(campaign.getDescription())
                .customMessage(campaign.getCustomMessage())
                .targetAudience(campaign.getTargetAudience())
                .segmentId(campaign.getSegmentId())
                .filterCriteria(campaign.getFilterCriteria())
                .recipientCount(campaign.getRecipientCount())
                .sentCount(campaign.getSentCount())
                .deliveredCount(campaign.getDeliveredCount())
                .failedCount(campaign.getFailedCount())
                .totalCost(campaign.getTotalCost())
                .status(campaign.getStatus())
                .scheduledAt(campaign.getScheduledAt())
                .startedAt(campaign.getStartedAt())
                .completedAt(campaign.getCompletedAt())
                .createdAt(campaign.getCreatedAt())
                .deliveryRate(campaign.getDeliveryRate());

        if (campaign.getTemplate() != null) {
            builder.templateId(campaign.getTemplate().getId())
                   .templateName(campaign.getTemplate().getName());
        }

        return builder.build();
    }
}
