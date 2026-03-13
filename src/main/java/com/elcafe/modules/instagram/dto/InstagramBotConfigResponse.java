package com.elcafe.modules.instagram.dto;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class InstagramBotConfigResponse {
    private Long id;
    private String appId;
    private String instagramAccountId;
    private Boolean isActive;
    private String welcomeMessage;
    private Boolean autoReplyEnabled;
    private String autoReplyTemplate;
    /** True when an access token is stored (never returned as plain text) */
    private boolean hasAccessToken;
    /** True when an app secret is stored */
    private boolean hasAppSecret;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static InstagramBotConfigResponse from(InstagramBotConfig c) {
        return InstagramBotConfigResponse.builder()
                .id(c.getId())
                .appId(c.getAppId())
                .instagramAccountId(c.getInstagramAccountId())
                .isActive(c.getIsActive())
                .welcomeMessage(c.getWelcomeMessage())
                .autoReplyEnabled(c.getAutoReplyEnabled())
                .autoReplyTemplate(c.getAutoReplyTemplate())
                .hasAccessToken(c.getAccessToken() != null && !c.getAccessToken().isBlank())
                .hasAppSecret(c.getAppSecret() != null && !c.getAppSecret().isBlank())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
