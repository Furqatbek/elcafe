package com.elcafe.modules.instagram.dto;

import lombok.Data;

/**
 * Create-and-send payload for an Instagram campaign. {@code targetAudience} is "ALL" (default) or
 * "REGISTERED"; {@code messageText} is the DM body sent to every targeted subscriber.
 */
@Data
public class InstagramCampaignRequest {
    private String name;
    private String messageText;
    private String targetAudience;

    /**
     * Optional promo image URL (V176). When present, {@code InstagramCampaignExecutor} sends it as a
     * leading image-attachment DM ahead of {@code messageText} — Instagram is a photo-first platform,
     * and a promo image is the whole point of a food marketing campaign. Null/blank means a text-only
     * campaign, exactly today's behaviour; {@code InstagramCampaignService} normalizes blank to null.
     */
    private String imageUrl;
}
