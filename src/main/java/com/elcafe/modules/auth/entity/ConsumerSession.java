package com.elcafe.modules.auth.entity;

import com.elcafe.modules.customer.entity.Customer;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a consumer session for phone-authenticated users
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "consumer_sessions", indexes = {
        @Index(name = "idx_consumer_sessions_phone_number", columnList = "phone_number"),
        @Index(name = "idx_consumer_sessions_session_token", columnList = "session_token"),
        @Index(name = "idx_consumer_sessions_refresh_token", columnList = "refresh_token"),
        @Index(name = "idx_consumer_sessions_expires_at", columnList = "expires_at"),
        @Index(name = "idx_consumer_sessions_is_active", columnList = "is_active")
})
public class ConsumerSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "session_token", nullable = false, unique = true, length = 512)
    private String sessionToken;

    @Column(name = "refresh_token", nullable = false, unique = true, length = 512)
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "refresh_expires_at", nullable = false)
    private LocalDateTime refreshExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_accessed_at")
    @Builder.Default
    private LocalDateTime lastAccessedAt = LocalDateTime.now();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Check if session is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if refresh token is expired
     */
    public boolean isRefreshExpired() {
        return LocalDateTime.now().isAfter(refreshExpiresAt);
    }

    /**
     * Check if session is valid (active and not expired)
     */
    public boolean isValid() {
        return isActive && !isExpired();
    }

    /**
     * Update last accessed timestamp
     */
    public void updateLastAccessed() {
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * Invalidate the session
     */
    public void invalidate() {
        this.isActive = false;
    }
}
