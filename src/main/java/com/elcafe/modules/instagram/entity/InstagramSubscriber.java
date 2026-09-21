package com.elcafe.modules.instagram.entity;

import com.elcafe.modules.customer.entity.Customer;
import jakarta.persistence.*;
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
@Table(name = "instagram_subscribers")
@EntityListeners(AuditingEntityListener.class)
public class InstagramSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Instagram Scoped User ID — unique per user per Meta app */
    @Column(nullable = false, unique = true, length = 50)
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
