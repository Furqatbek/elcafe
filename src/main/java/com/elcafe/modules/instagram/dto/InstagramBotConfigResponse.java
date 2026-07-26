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
    private Boolean privateReplyEnabled;
    private String privateReplyKeyword;
    private String privateReplyTemplate;
    private Long privateReplyPromotionId;
    /** True when an access token is stored (never returned as plain text) */
    private boolean hasAccessToken;
    /** True when an app secret is stored */
    private boolean hasAppSecret;
    /**
     * Estimated expiry of the stored access token (V175, ~60 days from when it was last set); null when
     * no token is stored. An ESTIMATE — Meta does not return the real expiry — for a "reconnect soon" UI
     * warning, not a guarantee.
     */
    private OffsetDateTime tokenExpiresAt;
    /**
     * False once a send under this config has hit Meta's invalid/expired-token error (code 190); the UI
     * should warn even though {@link #hasAccessToken} still reads true. Null only for a config predating
     * V175 that has not yet been saved through the new code path.
     */
    private Boolean tokenHealthy;
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
                .privateReplyEnabled(c.getPrivateReplyEnabled())
                .privateReplyKeyword(c.getPrivateReplyKeyword())
                .privateReplyTemplate(c.getPrivateReplyTemplate())
                .privateReplyPromotionId(c.getPrivateReplyPromotionId())
                .hasAccessToken(c.getAccessToken() != null && !c.getAccessToken().isBlank())
                .hasAppSecret(c.getAppSecret() != null && !c.getAppSecret().isBlank())
                .tokenExpiresAt(c.getTokenExpiresAt())
                .tokenHealthy(c.getTokenHealthy())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
