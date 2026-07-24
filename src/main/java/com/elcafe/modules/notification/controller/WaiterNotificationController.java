package com.elcafe.modules.notification.controller;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.repository.NotificationRepository;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Waiter-app-facing notification endpoints under the /waiter/notifications
 * namespace the mobile app already targets. This currently mirrors the
 * canonical NotificationController's mark-as-read; device registration and the
 * rest of the /waiter/notifications/* table land with the Expo push task.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/waiter/notifications")
@RequiredArgsConstructor
@Tag(name = "Waiter Notifications", description = "Notification endpoints for the waiter mobile app")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class WaiterNotificationController {

    private final NotificationRepository notificationRepository;

    /**
     * Mark a notification as read.
     * POST /api/v1/waiter/notifications/{id}/read — the exact call the app makes
     * on notification tap. Same behaviour as PATCH /api/v1/notifications/{id}/read.
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Notification>> markAsRead(@PathVariable Long id) {
        log.info("Waiter marking notification {} as read", id);

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));

        notification.markAsRead();
        notificationRepository.save(notification);

        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", notification));
    }
}
