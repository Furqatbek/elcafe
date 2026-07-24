package com.elcafe.modules.notification.controller;

import com.elcafe.modules.notification.dto.waiter.*;
import com.elcafe.modules.notification.service.WaiterNotificationService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Waiter mobile app notification API — the /api/v1/waiter/notifications
 * contract the app consumes. All endpoints are scoped to the authenticated
 * waiter via the X-Waiter-Id header (same convention as the rest of the
 * waiter API).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/waiter/notifications")
@RequiredArgsConstructor
@Tag(name = "Waiter Notifications", description = "Notification endpoints for the waiter mobile app")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class WaiterNotificationController {

    private final WaiterNotificationService service;

    // ---- Devices ----

    @PostMapping("/devices")
    public ResponseEntity<ApiResponse<RegisterDeviceResponse>> registerDevice(
            @RequestHeader("X-Waiter-Id") Long waiterId,
            @Valid @RequestBody RegisterDeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Device registered", service.registerDevice(waiterId, request)));
    }

    @PutMapping("/devices/{oldToken}")
    public ResponseEntity<ApiResponse<RegisterDeviceResponse>> rotateToken(
            @RequestHeader("X-Waiter-Id") Long waiterId,
            @PathVariable String oldToken,
            @Valid @RequestBody RotateTokenRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Token rotated", service.rotateToken(waiterId, oldToken, request.getToken())));
    }

    @DeleteMapping("/devices/{token}")
    public ResponseEntity<ApiResponse<Void>> unregisterDevice(@PathVariable String token) {
        service.unregisterDevice(token);
        return ResponseEntity.ok(ApiResponse.success("Device unregistered", null));
    }

    // ---- Preferences ----

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<WaiterNotificationPreferenceDto>> getPreferences(
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        return ResponseEntity.ok(ApiResponse.success("Preferences retrieved", service.getPreferences(waiterId)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<WaiterNotificationPreferenceDto>> updatePreferences(
            @RequestHeader("X-Waiter-Id") Long waiterId,
            @RequestBody WaiterNotificationPreferenceDto request) {
        return ResponseEntity.ok(
                ApiResponse.success("Preferences updated", service.updatePreferences(waiterId, request)));
    }

    // ---- Notifications ----

    @GetMapping
    public ResponseEntity<ApiResponse<WaiterNotificationListResponse>> list(
            @RequestHeader("X-Waiter-Id") Long waiterId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Boolean unreadOnly) {
        return ResponseEntity.ok(
                ApiResponse.success("Notifications retrieved", service.list(waiterId, page, limit, unreadOnly)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        return ResponseEntity.ok(
                ApiResponse.success("Unread count retrieved", Map.of("count", service.unreadCount(waiterId))));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @RequestHeader("X-Waiter-Id") Long waiterId) {
        service.markAllRead(waiterId);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PushNotificationDto>> getOne(
            @RequestHeader("X-Waiter-Id") Long waiterId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Notification retrieved", service.getOne(waiterId, id)));
    }

    /** POST /api/v1/waiter/notifications/{id}/read — the exact call the app makes on tap. */
    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<PushNotificationDto>> markAsRead(
            @RequestHeader("X-Waiter-Id") Long waiterId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", service.markRead(waiterId, id)));
    }
}
