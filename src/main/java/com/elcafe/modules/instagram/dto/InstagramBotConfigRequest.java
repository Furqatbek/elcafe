package com.elcafe.modules.instagram.dto;

import lombok.Data;

@Data
public class InstagramBotConfigRequest {
    private String appId;
    private String appSecret;
    private String accessToken;
    private String instagramAccountId;
    private String verifyToken;
    private Boolean isActive;
    private String welcomeMessage;
    private Boolean autoReplyEnabled;
    private String autoReplyTemplate;
    private Boolean privateReplyEnabled;
    private String privateReplyKeyword;
    private String privateReplyTemplate;
    private Long privateReplyPromotionId;
}
