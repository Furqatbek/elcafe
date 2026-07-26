package com.elcafe.modules.instagram.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * A webhook event this system has already acted on, recorded so a Meta re-delivery is a no-op.
 *
 * <p>Meta delivers webhooks at-least-once and re-sends on any non-2xx or timeout. Without a record of
 * what was already handled, a re-delivered messaging event re-advances the registration wizard or
 * re-runs the customer link, and a re-delivered comment event re-fires the public auto-reply.
 * {@link com.elcafe.modules.instagram.service.InstagramWebhookDedupService} checks-and-records the
 * event's Meta id here before dispatch; the {@code uq_ig_processed_event} unique constraint is the
 * arbiter when two re-deliveries race.
 *
 * <p>Per-tenant from birth (V167): {@code restaurantId} is bound to the config that owns the receiving
 * account, and the row is scoped by the §3.4 {@code restaurantFilter}. Uniqueness is per-tenant, so the
 * same Meta id delivered to two restaurants' accounts is two independent rows — an event is only ever a
 * duplicate within its own tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "instagram_processed_events",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_ig_processed_event",
        columnNames = {"restaurant_id", "event_id"})
)
// V167: per-tenant channel — scoped by the §3.4 restaurantFilter.
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class InstagramProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant — the restaurant whose Instagram account received the event. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    /**
     * Meta's own id for the event, namespaced by kind: a message/postback {@code mid} or a comment id.
     * Unique per tenant — that uniqueness is what makes a re-delivery detectable.
     */
    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;
}
