package com.elcafe.modules.push.entity;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.customer.entity.Customer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Stores web push notification subscription data for browsers.
 */
@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, length = 500)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, length = 500)
    private String authKey;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Update last used timestamp
     */
    public void markAsUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }

    /**
     * Deactivate the subscription
     */
    public void deactivate() {
        this.isActive = false;
    }
}
