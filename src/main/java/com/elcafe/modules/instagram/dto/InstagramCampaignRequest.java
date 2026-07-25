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
}
