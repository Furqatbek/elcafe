package com.elcafe.modules.notification.controller;

import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.enums.UserRole;
import com.elcafe.modules.notification.repository.NotificationRepository;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing notifications
 * Provides endpoints for all user roles to fetch and manage their notifications
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
        @RequestParam UserRole role,
        @RequestParam(required = false) Long userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
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
        @RequestParam UserRole role,
        @RequestParam Long userId
    ) {
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
        @RequestParam UserRole role,
        @RequestParam Long userId
    ) {
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
        @PathVariable Long orderId
    ) {
        log.info("Fetching notifications for order: {}", orderId);

        List<Notification> notifications = notificationRepository.findByOrderIdOrderByCreatedAtDesc(orderId);

        return ResponseEntity.ok(
            ApiResponse.success("Order notifications retrieved successfully", notifications)
        );
    }

    /**
     * Mark a notification as read.
     * Accepts both PATCH and POST on /api/v1/notifications/{id}/read — the
     * waiter mobile app issues a POST, while the web admin uses PATCH; both
     * map to the same handler so tap-to-mark-read works from either client.
     */
    @RequestMapping(value = "/{id}/read", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<ApiResponse<Notification>> markAsRead(@PathVariable Long id) {
        log.info("Marking notification {} as read", id);

        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Notification not found with id: " + id));

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
        @RequestParam UserRole role,
        @RequestParam Long userId
    ) {
        log.info("Marking all notifications as read for role: {}, userId: {}", role, userId);

        int count = notificationRepository.markAllAsReadForUser(role, userId);

        return ResponseEntity.ok(
            ApiResponse.success("All notifications marked as read", Map.of("updated", count))
        );
    }

    /**
     * Archive a notification
     * PATCH /api/v1/notifications/123/archive
     */
    @PatchMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<Notification>> archiveNotification(@PathVariable Long id) {
        log.info("Archiving notification {}", id);

        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Notification not found with id: " + id));

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
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable Long id) {
        log.info("Deleting notification {}", id);

        if (!notificationRepository.existsById(id)) {
            throw new RuntimeException("Notification not found with id: " + id);
        }

        notificationRepository.deleteById(id);

        return ResponseEntity.ok(
            ApiResponse.success("Notification deleted successfully", null)
        );
    }
}
