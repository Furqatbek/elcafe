package com.elcafe.modules.instagram.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * One inbound Instagram DM (V179) — the storage half of the agent-takeover inbox. Every inbound
 * TEXT-kind message {@link com.elcafe.modules.instagram.service.InstagramWebhookService} receives is
 * persisted here, best-effort, whether or not it also reaches the registration wizard — see that
 * class's {@code recordInboundTextBestEffort}.
 *
 * <p>Deliberately a separate table from {@link InstagramLog} (the V171 SEND audit trail), not a
 * {@code direction} column bolted onto it: {@code InstagramStatisticsService} counts {@code
 * instagram_logs} rows by status as OUTBOUND send volume, and mixing inbound rows into that table
 * would silently inflate every one of those counts. See the V179 migration comment for the full
 * reasoning.
 *
 * <ul>
 *   <li>{@code igsid} is denormalised exactly like {@link InstagramLog#getIgsid()} — a message can
 *       arrive before any subscriber row exists (e.g. a stranger's first-ever message is a STOP/
 *       SUBSCRIBE keyword, which {@code InstagramBotService} deliberately never creates a subscriber
 *       row for), so the sender must still be identifiable without one.</li>
 *   <li>{@code subscriber} is {@code ON DELETE CASCADE}, unlike {@link InstagramLog}'s {@code SET
 *       NULL}: this row IS the customer's own words, so once the subscriber is erased
 *       ({@code InstagramBotService#eraseSubscriber}) this content must not outlive it either. The
 *       cascade is only the backstop; {@code InstagramMessageLogger#eraseSubscriberLogs} erases
 *       explicitly and immediately — the same dual approach V171 already established for {@link
 *       InstagramLog}.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "instagram_inbound_message")
// V179: per-tenant from birth — scoped by the §3.4 restaurantFilter, like every Instagram table since V163.
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class InstagramInboundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    /** The subscriber this message came from, when one exists yet — see class javadoc for when it may
     *  legitimately be null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id")
    private InstagramSubscriber subscriber;

    /** Instagram Scoped User ID of the sender — always known, denormalised (see class javadoc). */
    @Column(nullable = false, length = 50)
    private String igsid;

    /** What the customer typed. Only populated for {@code InstagramInboundKind.TEXT} events. */
    @Column(name = "message_text", columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
}
