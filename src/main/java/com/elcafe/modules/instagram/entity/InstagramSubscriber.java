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

    public void touch() {
        this.lastInteractionAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public String getDisplayNameOrFallback() {
        if (displayName != null) return displayName;
        if (username != null)    return "@" + username;
        return "User " + igsid;
    }
}
