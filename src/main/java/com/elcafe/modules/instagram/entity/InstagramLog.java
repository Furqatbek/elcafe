package com.elcafe.modules.instagram.entity;

import com.elcafe.modules.instagram.enums.InstagramMessageType;
import com.elcafe.modules.sms.enums.MessageStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * One Instagram send attempt: a DM, a campaign broadcast, or a public comment reply. This is the
 * per-tenant substrate that Instagram statistics, retry and resume are built on — every
 * {@code InstagramApiClient} send this module fires is meant to leave exactly one row here, whatever
 * the outcome, via {@code InstagramMessageLogger}.
 *
 * <p>Modeled on {@link com.elcafe.modules.telegram.entity.TelegramLog} but Instagram-shaped:
 * <ul>
 *   <li>{@code igsid} (not a numeric {@code telegram_user_id}) is the recipient identifier, denormalised
 *       here the same way {@link InstagramCampaignRecipient} denormalises it — so a row survives even
 *       when there is no linked {@link InstagramSubscriber} (a comment auto-reply has none; the {@code
 *       igsid} column then carries the comment id instead, since a public reply has no DM recipient).</li>
 *   <li>{@code instagramMessageId} is Meta's opaque {@code mid} STRING (Telegram's message id is
 *       numeric), and {@code campaignId} is a plain nullable id rather than a {@code @ManyToOne} — the
 *       executor only ever has the id on hand, not a loaded {@link InstagramCampaign}.</li>
 *   <li>Timestamps are {@link OffsetDateTime}/{@code TIMESTAMPTZ}, matching every other Instagram entity
 *       introduced since V163 ({@link InstagramSubscriber}, {@link InstagramBotConfig}, {@link
 *       InstagramCampaign}) rather than {@code TelegramLog}'s older {@code LocalDateTime}.</li>
 *   <li>Only {@code restaurant_id} is NOT NULL. Every other column stays nullable on purpose: a logging
 *       failure must never cost a send, and {@code InstagramMessageLogger} swallows any save error — but
 *       an avoidable NOT NULL violation would silently drop the one row that attempt should have left
 *       behind.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "instagram_logs")
@EntityListeners(AuditingEntityListener.class)
// V171: per-tenant from birth (like instagram_campaign, V166) — scoped by the §3.4 restaurantFilter.
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class InstagramLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    /** The subscriber this message concerns, when there is one (null for a comment auto-reply). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id")
    private InstagramSubscriber subscriber;

    /**
     * Instagram Scoped User ID of the recipient, denormalised so this row never needs a lazy fetch to
     * know who it was for. For {@link InstagramMessageType#AUTO_REPLY} — a public reply, not a DM —
     * there is no recipient igsid, so this instead carries the Meta comment id being replied to.
     */
    @Column(length = 50)
    private String igsid;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 50)
    private InstagramMessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private MessageStatus status = MessageStatus.PENDING;

    /**
     * Meta's message id (the {@code mid}) for a delivered DM. Currently always null: {@code
     * InstagramApiClient} discards the Graph response body, so no send path can populate this yet. The
     * column exists now so a future correlation with the webhook's delivery receipt
     * ({@code InstagramWebhookService} already ignores {@code event.get("delivery")}) has somewhere to
     * write {@link #deliveredAt} without another migration.
     */
    @Column(name = "instagram_message_id")
    private String instagramMessageId;

    /** The content sent — a DM's text or a comment reply's text. */
    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Meta's numeric error code ({@code InstagramSendResult.code()}), null when delivered. */
    @Column(name = "error_code")
    private Integer errorCode;

    /** The campaign this send belongs to, or null outside a campaign. Plain id — see class javadoc. */
    @Column(name = "campaign_id")
    private Long campaignId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    /** Set once a delivery receipt confirms it — not yet wired up (see {@link #instagramMessageId}). */
    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    // No columnDefinition: @JdbcTypeCode(SqlTypes.JSON) alone is Hibernate 6's dialect-aware JSON
    // mapping. PostgreSQLDialect resolves it to a native jsonb column — matching V171's `metadata JSONB`,
    // so ddl-auto:validate accepts it in production — while H2Dialect resolves it to H2's native JSON
    // type, so the @DataJpaTest schema generation succeeds. A literal columnDefinition = "jsonb" would
    // break H2 (it rejects the "JSONB" keyword — the reason TelegramLog and 14 other jsonb-column
    // entities have no H2 repository test), and a literal "json" would silently drift from the migration.
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
}
