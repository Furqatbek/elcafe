package com.elcafe.modules.instagram.entity;

import com.elcafe.common.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "instagram_bot_config")
@EntityListeners(AuditingEntityListener.class)
// V163: Instagram is a per-tenant channel — each restaurant connects its own Instagram business
// account. Scoped by the §3.4 restaurantFilter like the owner bot's config (V73).
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class InstagramBotConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant. One active config per restaurant (uq_ig_config_active_per_restaurant). */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    /** Meta App ID */
    @Column(name = "app_id", length = 50)
    private String appId;

    /**
     * Meta App Secret — used to verify webhook signatures. Encrypted at rest (V169): a DB dump must not
     * yield the secret that lets someone forge signed webhook events. Column widened to TEXT to hold the
     * ciphertext envelope.
     */
    @Column(name = "app_secret", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String appSecret;

    /**
     * Long-lived Page Access Token used for all Graph API calls. Encrypted at rest (V169): this token is
     * full posting/messaging control of the merchant's account — the single most sensitive value here.
     */
    @Column(name = "access_token", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
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

    /**
     * When true, a comment matching {@link #privateReplyKeyword} gets a Meta "private reply" DM (V173)
     * — the "comment MENU and we'll DM you" growth mechanic. Independent of {@link #autoReplyEnabled}:
     * a config can run the public comment reply, the private DM, both, or neither.
     */
    @Column(name = "private_reply_enabled", nullable = false)
    @Builder.Default
    private Boolean privateReplyEnabled = false;

    /**
     * Case-insensitive substring match against the comment text (see
     * {@code InstagramWebhookService#matchesPrivateReplyKeyword}); null/blank never matches, even when
     * {@link #privateReplyEnabled} is true.
     */
    @Column(name = "private_reply_keyword", length = 100)
    private String privateReplyKeyword;

    /** DM text sent as the private reply. Supports {code} — substituted with a minted coupon, or
     *  stripped when no coupon is available. */
    @Column(name = "private_reply_template", columnDefinition = "TEXT")
    private String privateReplyTemplate;

    /**
     * Promotion a single coupon code is minted from for the {@code {code}} placeholder; null sends the
     * template with the placeholder stripped instead. Plain id, not a {@code @ManyToOne} — this entity
     * has no JPA relations to other modules (see {@link #restaurantId}), and the webhook path
     * re-validates the promotion belongs to this same restaurant itself rather than navigating this as
     * an association. FK is ON DELETE SET NULL (V173): a promotion deleted elsewhere silently disables
     * the coupon (falls back to the {@code {code}}-stripped template) instead of leaving a dangling id.
     */
    @Column(name = "private_reply_promotion_id")
    private Long privateReplyPromotionId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
