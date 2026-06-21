package com.elcafe.modules.notification.entity;

import com.elcafe.modules.notification.enums.NotificationStatus;
import com.elcafe.modules.notification.enums.NotificationType;
import com.elcafe.modules.notification.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Notification entity to track order state notifications for different roles
 * Stores notifications for ADMIN, RESTAURANT, CUSTOMER, COURIER, KITCHEN, WAITER
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_user_role", columnList = "user_role"),
    @Index(name = "idx_user_id_role", columnList = "user_id, user_role"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * §3.7 follow-up: the owning tenant. Backfilled from the notification's order (V155) and stamped
     * on create by NotificationService. Nullable so a stray un-backfillable row stays invisible to
     * tenant-scoped reads (visible only to SUPER_ADMIN's null scope) rather than failing the backfill.
     */
    @Column(name = "restaurant_id")
    private Long restaurantId;

    /**
     * The role this notification is for (ADMIN, RESTAURANT, CUSTOMER, COURIER, KITCHEN, WAITER)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 20)
    private UserRole userRole;

    /**
     * The specific user ID (optional - if null, notification is for all users with that role)
     * For CUSTOMER: customer_id
     * For RESTAURANT: restaurant_id
     * For COURIER: courier_id
     * For ADMIN: admin_user_id
     * For KITCHEN: restaurant_id (kitchen staff)
     * For WAITER: waiter_id
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Type of notification (NEW_ORDER, ORDER_ACCEPTED, ORDER_READY, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    /**
     * The notification title
     */
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * The notification message
     */
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * Related order ID (if applicable)
     */
    @Column(name = "order_id")
    private Long orderId;

    /**
     * Related order number for display
     */
    @Column(name = "order_number", length = 50)
    private String orderNumber;

    /**
     * Status of the notification (UNREAD, READ, ARCHIVED)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.UNREAD;

    /**
     * When the notification was created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When the notification was read
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * Additional metadata as JSON (optional)
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Priority level (1 = highest, 5 = lowest)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 3;

    /**
     * Mark notification as read
     */
    public void markAsRead() {
        this.status = NotificationStatus.READ;
        this.readAt = LocalDateTime.now();
    }

    /**
     * Mark notification as archived
     */
    public void markAsArchived() {
        this.status = NotificationStatus.ARCHIVED;
    }
}
