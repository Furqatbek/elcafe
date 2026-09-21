package com.elcafe.common.audit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Comprehensive audit log for security-critical operations.
 * Captures who, what, when, where for all financial and sensitive operations.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_audit_user", columnList = "user_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_created", columnList = "created_at"),
        @Index(name = "idx_audit_order", columnList = "order_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // WHO performed the action
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "user_role", length = 50)
    private String userRole;

    // WHAT action was performed
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "action_detail", length = 500)
    private String actionDetail;

    // WHICH entity was affected
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    // Order reference for financial operations
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    // Restaurant context
    @Column(name = "restaurant_id")
    private Long restaurantId;

    // Financial impact (for payment/refund/void operations)
    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    // WHERE the action originated
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "terminal_id", length = 100)
    private String terminalId;

    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    // Session tracking
    @Column(name = "session_id", length = 100)
    private String sessionId;

    // Previous and new values for change tracking
    @Column(name = "previous_value", columnDefinition = "TEXT")
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    // Authorization context
    @Column(name = "authorized_by_id")
    private Long authorizedById;

    @Column(name = "authorized_by_username", length = 100)
    private String authorizedByUsername;

    @Column(name = "authorization_reason", length = 500)
    private String authorizationReason;

    // Result of the action
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuditResult result = AuditResult.SUCCESS;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // WHEN
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public enum AuditResult {
        SUCCESS,
        FAILURE,
        DENIED,
        PENDING_APPROVAL
    }
}
