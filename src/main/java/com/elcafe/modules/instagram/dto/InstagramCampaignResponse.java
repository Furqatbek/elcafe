package com.elcafe.modules.instagram.dto;

import com.elcafe.modules.instagram.entity.InstagramCampaign;

import java.time.OffsetDateTime;

/**
 * Campaign view for the operator: identity, message, audience, live status and the sent/failed
 * counters the executor updates as it runs.
 */
public record InstagramCampaignResponse(
        Long id,
        String name,
        String messageText,
        String targetAudience,
        String status,
        int recipientCount,
        int sentCount,
        int failedCount,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt) {

    public static InstagramCampaignResponse from(InstagramCampaign c) {
        return new InstagramCampaignResponse(
                c.getId(),
                c.getName(),
                c.getMessageText(),
                c.getTargetAudience() != null ? c.getTargetAudience().name() : null,
                c.getStatus() != null ? c.getStatus().name() : null,
                c.getRecipientCount() != null ? c.getRecipientCount() : 0,
                c.getSentCount() != null ? c.getSentCount() : 0,
                c.getFailedCount() != null ? c.getFailedCount() : 0,
                c.getStartedAt(),
                c.getCompletedAt(),
                c.getCreatedAt());
    }
}
