package com.elcafe.modules.instagram.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "instagram_bot_config")
@EntityListeners(AuditingEntityListener.class)
public class InstagramBotConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Meta App ID */
    @Column(name = "app_id", length = 50)
    private String appId;

    /** Meta App Secret — used to verify webhook signatures */
    @Column(name = "app_secret", length = 200)
    private String appSecret;

    /** Long-lived Page Access Token used for all Graph API calls */
    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    /** Numeric Instagram Business Account ID */
    @Column(name = "instagram_account_id", length = 50)
    private String instagramAccountId;

    /** Random string configured in Meta Webhook settings for challenge verification */
    @Column(name = "verify_token", length = 200)
    private String verifyToken;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = false;

    /** Sent to a user on their first message */
    @Column(name = "welcome_message", columnDefinition = "TEXT")
    private String welcomeMessage;

    /** When true, auto-reply to new comments on business posts */
    @Column(name = "auto_reply_enabled")
    @Builder.Default
    private Boolean autoReplyEnabled = false;

    /** Template text sent as a reply to comments. Supports {username}. */
    @Column(name = "auto_reply_template", columnDefinition = "TEXT")
    private String autoReplyTemplate;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
