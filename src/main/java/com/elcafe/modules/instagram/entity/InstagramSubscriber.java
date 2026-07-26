package com.elcafe.modules.instagram.entity;

import com.elcafe.modules.customer.entity.Customer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "instagram_subscribers",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_ig_subscriber_restaurant_igsid",
        columnNames = {"restaurant_id", "igsid"})
)
@EntityListeners(AuditingEntityListener.class)
// V163: per-tenant channel — scoped by the §3.4 restaurantFilter.
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class InstagramSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant — the restaurant whose Instagram account this person messaged. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    /**
     * Instagram Scoped User ID — unique per user per Meta app. Since each restaurant connects its
     * own app/account (V163), uniqueness is per-tenant: the same person messaging two restaurants is
     * two independent subscribers.
     */
    @Column(nullable = false, length = 50)
    private String igsid;

    @Column(length = 100)
    private String username;

    /** Full name entered by the user during registration wizard */
    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(length = 30)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    /**
     * Conversation step. Registration wizard:
     * AWAITING_NAME | AWAITING_PHONE | AWAITING_BIRTHDAY |
     * AWAITING_ADDRESS | AWAITING_MORE_ADDRESSES | REGISTERED
     * Wave 7 in-DM ordering (only reachable FROM REGISTERED, returns TO REGISTERED):
     * ORDER_BROWSING | ORDER_QUANTITY | ORDER_CONFIRMING
     */
    @Column(name = "conversation_state", length = 30)
    private String conversationState;

    /**
     * In-progress Instagram in-DM order cart (Wave 7, V181). Persisted as a single JSONB document via
     * Hibernate's dialect-aware JSON type — {@code jsonb} on Postgres, a JSON-typed VARCHAR under the H2
     * test dialect — never a literal {@code columnDefinition = "jsonb"} (which would break H2 schema
     * generation). {@code null} means no order is in progress (the default, and what checkout/cancel
     * resets it to); a non-null list holds one {@link InstagramCartLine} per chosen product. This is the
     * whole cart mechanism — deliberately NOT a parallel cart-entity system like
     * {@code SelfServiceOrderService}'s dedicated cart rows.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "order_cart")
    private List<InstagramCartLine> orderCart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_blocked")
    @Builder.Default
    private Boolean isBlocked = false;

    /**
     * Marketing consent (V174). Defaults to true — see the migration's comment for the full
     * grandfathering rationale: this column must not silently cut off the reach every restaurant
     * already had the day it shipped, so every subscriber keeps receiving campaigns exactly as before
     * until they type a STOP-family keyword. {@link com.elcafe.modules.instagram.repository.
     * InstagramSubscriberRepository}'s campaign-audience finders ({@code findAllActiveNotBlockedSince},
     * {@code findAllRegisteredSince}) both require this to be true, regardless of ALL vs REGISTERED
     * targeting or the 24h messaging window — so a campaign never DMs someone who opted out.
     */
    @Column(name = "marketing_opt_in", nullable = false)
    @Builder.Default
    private Boolean marketingOptIn = true;

    /** When {@link #marketingOptIn} was last flipped to false by a STOP-family keyword; null while opted in. */
    @Column(name = "opted_out_at")
    private OffsetDateTime optedOutAt;

    @Column(name = "subscribed_at")
    private OffsetDateTime subscribedAt;

    @Column(name = "last_interaction_at")
    private OffsetDateTime lastInteractionAt;

    /**
     * Human-agent takeover (V179). Null or a past instant (the default, and the state a lapsed
     * takeover naturally decays to) means the registration wizard answers this subscriber exactly as
     * before; a future instant means a human agent has claimed the thread via the new Instagram inbox
     * ({@code InstagramInboxService#takeover}) and {@code InstagramWebhookService} must keep storing
     * inbound messages but skip dispatching them to {@code InstagramBotService#handleIncomingMessage}
     * until this lapses or an explicit {@code InstagramInboxService#release} clears it back to null.
     * Deliberately owned and read entirely by the webhook/inbox layer — {@code InstagramBotService}
     * itself never touches this column, since a later feature rewrites its conversation flow.
     */
    @Column(name = "human_handoff_until")
    private OffsetDateTime humanHandoffUntil;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * Optimistic-lock version (V168). Instagram webhooks fan out across the {@code @Async} pool, so two
     * DMs from one sender can hit separate transactions at once; without this, both read the same row
     * and the later commit silently overwrites the earlier — a lost wizard-state update. Hibernate now
     * turns that collision into an optimistic-lock failure, which {@code InstagramBotService} retries
     * against the winner's committed state. (Telegram needs none of this: its updates arrive serialized
     * on the long-polling thread.)
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    public void touch() {
        this.lastInteractionAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public String getDisplayNameOrFallback() {
        if (displayName != null) return displayName;
        if (username != null)    return "@" + username;
        return "User " + igsid;
    }
}
