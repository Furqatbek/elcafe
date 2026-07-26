package com.elcafe.modules.instagram.entity;

import com.elcafe.modules.customer.entity.Customer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
     * Wizard step:
     * AWAITING_NAME | AWAITING_PHONE | AWAITING_BIRTHDAY |
     * AWAITING_ADDRESS | AWAITING_MORE_ADDRESSES | REGISTERED
     */
    @Column(name = "conversation_state", length = 30)
    private String conversationState;

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
