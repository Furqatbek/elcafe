package com.elcafe.modules.telegram.entity;

import com.elcafe.modules.customer.entity.Customer;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
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
    name = "telegram_subscribers",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tg_subscriber_restaurant_user",
        columnNames = {"restaurant_id", "telegram_user_id"})
)
@EntityListeners(AuditingEntityListener.class)
// V164: Telegram is a per-tenant channel — scoped by the §3.4 restaurantFilter.
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class TelegramSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant — the restaurant whose bot this person subscribed to. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    // Uniqueness is per-tenant (uq_tg_subscriber_restaurant_user, V164): one Telegram account
    // can subscribe to several restaurants' bots, each an independent subscriber.
    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(length = 100)
    private String username;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    /** Full name as entered by the user during bot registration. */
    @Column(name = "display_name", length = 200)
    private String displayName;

    /** Phone number collected during bot registration. */
    @Column(length = 30)
    private String phone;

    /** Birthday collected during bot registration (optional). */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /**
     * Tracks the current registration step.
     * Values: AWAITING_NAME | AWAITING_PHONE | AWAITING_BIRTHDAY | AWAITING_LOCATION |
     *         AWAITING_MORE_LOCATIONS | REGISTERED
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

    public String getDisplayName() {
        // The name the user typed during bot-wizard registration wins. This getter used to shadow the
        // stored display_name field entirely (derived names only), so wizard-collected names were saved
        // but never used in greetings/campaigns.
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (username != null) {
            return "@" + username;
        }
        return "User " + telegramUserId;
    }

    public void updateLastInteraction() {
        this.lastInteractionAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
