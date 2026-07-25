package com.elcafe.modules.instagram.dto;

import com.elcafe.modules.instagram.entity.InstagramCampaignRecipient;

import java.time.OffsetDateTime;

/** Per-recipient delivery record: which subscriber, whether they were sent, and why not if not. */
public record InstagramCampaignRecipientResponse(
        Long id,
        String igsid,
        String status,
        OffsetDateTime sentAt,
        String errorMessage) {

    public static InstagramCampaignRecipientResponse from(InstagramCampaignRecipient r) {
        return new InstagramCampaignRecipientResponse(
                r.getId(),
                r.getIgsid(),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getSentAt(),
                r.getErrorMessage());
    }
}
