package com.elcafe.modules.ownerbot.entity;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "owner_telegram_subscribers")
@EntityListeners(AuditingEntityListener.class)
public class OwnerTelegramSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;

    @Column(length = 100)
    private String username;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Column(length = 50)
    private String role; // OWNER, MANAGER, CHEF, WAITER

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "verification_code", length = 10)
    private String verificationCode;

    @Column(name = "verification_expires_at")
    private LocalDateTime verificationExpiresAt;

    @Column(name = "subscribed_at")
    private LocalDateTime subscribedAt;

    @Column(name = "last_interaction_at")
    private LocalDateTime lastInteractionAt;

    @OneToOne(mappedBy = "subscriber", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private OwnerNotificationSettings notificationSettings;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getDisplayName() {
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
        this.lastInteractionAt = LocalDateTime.now();
    }

    public boolean canReceiveNotification(String type) {
        if (!isActive || !isVerified) {
            return false;
        }
        if (notificationSettings == null) {
            return true; // Default to yes if no settings
        }
        return notificationSettings.isNotificationEnabled(type);
    }
}
