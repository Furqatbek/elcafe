package com.elcafe.modules.notification.controller;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;
import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.enums.UserRole;
import com.elcafe.modules.notification.repository.NotificationRepository;
import com.elcafe.security.CustomerPrincipal;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing notifications
 * Provides endpoints for all user roles to fetch and manage their notifications
 *
 * <p>§3.7 IDOR fix: a consumer principal (CustomerPrincipal) may only ever see/mutate its OWN
 * notifications. For consumers the (role, userId) is forced to (CUSTOMER, principal.getId()), so any
 * client-supplied role/userId is ignored, and the by-id endpoints verify per-notification ownership.
 * Staff/admin principals retain the existing behaviour — their cross-tenant hardening (and adding a
 * tenant filter to the un-scoped Notification entity) is tracked separately.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    /**
     * Get all notifications for a specific user
     * GET /api/v1/notifications?role=CUSTOMER&userId=123&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Notification>>> getNotifications(
        @AuthenticationPrincipal CustomerPrincipal consumer,
        @RequestParam(required = false) UserRole role,
        @RequestParam(required = false) Long userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        if (consumer != null) {           // a consumer may only read its own notifications
            role = UserRole.CUSTOMER;
            userId = consumer.getId();
        }
        requireRole(role);
        log.info("Fetching notifications for role: {}, userId: {}", role, userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notifications;

        if (userId != null) {
            // Get both specific and broadcast notifications for this user
            notifications = notificationRepository.findAllForUser(role, userId, pageable);
        } else {
            // Get only broadcast notifications (userId is null)
            notifications = notificationRepository.findByUserRoleAndUserIdIsNullOrderByCreatedAtDesc(role, pageable);
        }

        return ResponseEntity.ok(
            ApiResponse.success("Notifications retrieved successfully", notifications)
        );
    }

    /**
     * Get unread notifications for a user
     * GET /api/v1/notifications/unread?role=CUSTOMER&userId=123
     */
    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<Notification>>> getUnreadNotifications(
        @AuthenticationPrincipal CustomerPrincipal consumer,
        @RequestParam(required = false) UserRole role,
        @RequestParam(required = false) Long userId
    ) {
        if (consumer != null) {
            role = UserRole.CUSTOMER;
            userId = consumer.getId();
        }
        requireRole(role);
        log.info("Fetching unread notifications for role: {}, userId: {}", role, userId);

        List<Notification> unreadNotifications = notificationRepository.findUnreadForUser(role, userId);

        return ResponseEntity.ok(
            ApiResponse.success("Unread notifications retrieved successfully", unreadNotifications)
        );
    }

    /**
     * Get unread notification count
     * GET /api/v1/notifications/unread/count?role=CUSTOMER&userId=123
     */
    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
        @AuthenticationPrincipal CustomerPrincipal consumer,
        @RequestParam(required = false) UserRole role,
        @RequestParam(required = false) Long userId
    ) {
        if (consumer != null) {
            role = UserRole.CUSTOMER;
            userId = consumer.getId();
        }
        requireRole(role);
        log.info("Fetching unread count for role: {}, userId: {}", role, userId);

        Long count = notificationRepository.countUnreadForUser(role, userId);

        return ResponseEntity.ok(
            ApiResponse.success("Unread count retrieved successfully", Map.of("count", count))
        );
    }

    /**
     * Get notifications for a specific order
     * GET /api/v1/notifications/order/7
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<List<Notification>>> getOrderNotifications(
        @AuthenticationPrincipal CustomerPrincipal consumer,
        @PathVariable Long orderId
    ) {
        log.info("Fetching notifications for order: {}", orderId);

        List<Notification> notifications = notificationRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        if (consumer != null) {
            // a consumer only sees its own notifications for the order, never other roles'/customers'
            notifications = notifications.stream()
                .filter(n -> n.getUserRole() == UserRole.CUSTOMER && consumer.getId().equals(n.getUserId()))
                .toList();
        }

        return ResponseEntity.ok(
            ApiResponse.success("Order notifications retrieved successfully", notifications)
        );
    }

    /**
     * Mark a notification as read
     * PATCH /api/v1/notifications/123/read
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Notification>> markAsRead(
        @AuthenticationPrincipal CustomerPrincipal consumer,
        @PathVariable Long id
    ) {
        log.info("Marking notification {} as read", id);

        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        assertConsumerOwnership(notification, consumer);

        notification.markAsRead();
        notificationRepository.save(notification);

        return ResponseEntity.ok(
            ApiResponse.success("Notification marked as read", notification)
        );
    }

    /**
     * Mark all notifications as read for a user
     * PATCH /api/v1/notifications/mark-all-read?role=CUSTOMER&userId=123
     */
    @PatchMapping("/mark-all-read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(
        @AuthenticationPrincipal CustomerPrincipal consumer,
        @RequestParam(required = false) UserRole role,
        @RequestParam(required = false) Long userId
    ) {
        if (consumer != null) {
            role = UserRole.CUSTOMER;
            userId = consumer.getId();
        }
        requireRole(role);
        log.info("Marking all notifications as read for role: {}, userId: {}", role, userId);

        // Scope the bulk update to the caller's tenant (null for SUPER_ADMIN / unbound). The
        // @Filter does not cover bulk JPQL updates, so the tenant is passed explicitly.
        int count = notificationRepository.markAllAsReadForUser(role, userId, TenantContext.getRestaurantId());

        return ResponseEntity.ok(
            ApiResponse.success("All notifications marked as read", Map.of("updated", count))
        );
    }

    /**
     * Archive a notification
     * PATCH /api/v1/notifications/123/archive
     */
    @PatchMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<Notification>> archiveNotification(
        @AuthenticationPrincipal CustomerPrincipal consumer,
        @PathVariable Long id
    ) {
        log.info("Archiving notification {}", id);

        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        assertConsumerOwnership(notification, consumer);

        notification.markAsArchived();
        notificationRepository.save(notification);

        return ResponseEntity.ok(
            ApiResponse.success("Notification archived", notification)
        );
    }

    /**
     * Delete a notification
     * DELETE /api/v1/notifications/123
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
        @AuthenticationPrincipal CustomerPrincipal consumer,
        @PathVariable Long id
    ) {
        log.info("Deleting notification {}", id);

        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        assertConsumerOwnership(notification, consumer);

        notificationRepository.delete(notification);

        return ResponseEntity.ok(
            ApiResponse.success("Notification deleted successfully", null)
        );
    }

    /**
     * §3.7: when the caller is a consumer, the notification must be its own (CUSTOMER role + matching
     * userId); otherwise reject. Non-consumer principals (null here) are unaffected.
     */
    private void assertConsumerOwnership(Notification notification, CustomerPrincipal consumer) {
        if (consumer != null
                && (notification.getUserRole() != UserRole.CUSTOMER
                    || !consumer.getId().equals(notification.getUserId()))) {
            throw new AccessDeniedException("Cannot access another user's notification");
        }
    }

    private void requireRole(UserRole role) {
        if (role == null) {
            throw new BadRequestException("role is required");
        }
    }
}
