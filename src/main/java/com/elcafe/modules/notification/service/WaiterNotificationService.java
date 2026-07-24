package com.elcafe.modules.notification.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.notification.dto.waiter.*;
import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.entity.WaiterDevice;
import com.elcafe.modules.notification.entity.WaiterNotificationPreference;
import com.elcafe.modules.notification.enums.NotificationStatus;
import com.elcafe.modules.notification.enums.NotificationType;
import com.elcafe.modules.notification.enums.UserRole;
import com.elcafe.modules.notification.repository.NotificationRepository;
import com.elcafe.modules.notification.repository.WaiterDeviceRepository;
import com.elcafe.modules.notification.repository.WaiterNotificationPreferenceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backs the /api/v1/waiter/notifications contract the mobile app consumes.
 * Notification records reuse the shared {@link Notification} entity scoped to
 * (WAITER, waiterId); devices and preferences have their own tables.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaiterNotificationService {

    private static final UserRole ROLE = UserRole.WAITER;

    /** The waiter app's own NotificationType taxonomy. */
    private static final Set<String> APP_TYPES = Set.of(
            "ORDER_STATUS", "TABLE_READY", "KITCHEN_ALERT", "NEW_ORDER",
            "PAYMENT_RECEIVED", "SYSTEM_ALERT", "SHIFT_REMINDER");

    private final NotificationRepository notificationRepository;
    private final WaiterDeviceRepository deviceRepository;
    private final WaiterNotificationPreferenceRepository preferenceRepository;
    private final ExpoPushService expoPushService;
    private final ObjectMapper objectMapper;

    // ---- Devices (1-3) ----

    @Transactional
    public RegisterDeviceResponse registerDevice(Long waiterId, RegisterDeviceRequest req) {
        // Upsert by token — the app auto-registers on every launch.
        WaiterDevice device = deviceRepository.findByToken(req.getToken())
                .orElseGet(WaiterDevice::new);
        device.setWaiterId(waiterId);
        device.setToken(req.getToken());
        device.setPlatform(req.getPlatform());
        device.setDeviceId(req.getDeviceId());
        device.setDeviceName(req.getDeviceName());
        device.setAppVersion(req.getAppVersion());
        return toDeviceResponse(deviceRepository.save(device));
    }

    @Transactional
    public RegisterDeviceResponse rotateToken(Long waiterId, String oldToken, String newToken) {
        WaiterDevice old = deviceRepository.findByToken(oldToken)
                .orElseThrow(() -> new ResourceNotFoundException("No device registered for that token"));
        // If the new token already exists, keep that row and drop the old one.
        WaiterDevice existingNew = deviceRepository.findByToken(newToken).orElse(null);
        if (existingNew != null && !existingNew.getId().equals(old.getId())) {
            deviceRepository.delete(old);
            existingNew.setWaiterId(waiterId);
            return toDeviceResponse(deviceRepository.save(existingNew));
        }
        old.setWaiterId(waiterId);
        old.setToken(newToken);
        return toDeviceResponse(deviceRepository.save(old));
    }

    @Transactional
    public void unregisterDevice(String token) {
        deviceRepository.findByToken(token).ifPresent(deviceRepository::delete);
    }

    // ---- Preferences (4-5) ----

    @Transactional(readOnly = true)
    public WaiterNotificationPreferenceDto getPreferences(Long waiterId) {
        return preferenceRepository.findByWaiterId(waiterId)
                .map(this::toPreferenceDto)
                .orElseGet(WaiterNotificationService::defaultPreferences);
    }

    @Transactional
    public WaiterNotificationPreferenceDto updatePreferences(Long waiterId, WaiterNotificationPreferenceDto dto) {
        WaiterNotificationPreference pref = preferenceRepository.findByWaiterId(waiterId)
                .orElseGet(() -> WaiterNotificationPreference.builder().waiterId(waiterId).build());
        if (dto.getOrderUpdates() != null) pref.setOrderUpdates(dto.getOrderUpdates());
        if (dto.getTableReady() != null) pref.setTableReady(dto.getTableReady());
        if (dto.getKitchenAlerts() != null) pref.setKitchenAlerts(dto.getKitchenAlerts());
        if (dto.getNewOrders() != null) pref.setNewOrders(dto.getNewOrders());
        if (dto.getPaymentNotifications() != null) pref.setPaymentNotifications(dto.getPaymentNotifications());
        if (dto.getSystemAlerts() != null) pref.setSystemAlerts(dto.getSystemAlerts());
        if (dto.getShiftReminders() != null) pref.setShiftReminders(dto.getShiftReminders());
        if (dto.getSoundEnabled() != null) pref.setSoundEnabled(dto.getSoundEnabled());
        if (dto.getVibrationEnabled() != null) pref.setVibrationEnabled(dto.getVibrationEnabled());
        if (dto.getQuietHoursEnabled() != null) pref.setQuietHoursEnabled(dto.getQuietHoursEnabled());
        if (dto.getQuietHoursStart() != null) pref.setQuietHoursStart(dto.getQuietHoursStart());
        if (dto.getQuietHoursEnd() != null) pref.setQuietHoursEnd(dto.getQuietHoursEnd());
        return toPreferenceDto(preferenceRepository.save(pref));
    }

    // ---- Notifications (6-10) ----

    @Transactional(readOnly = true)
    public WaiterNotificationListResponse list(Long waiterId, Integer page, Integer limit, Boolean unreadOnly) {
        int p = page != null ? Math.max(page, 0) : 0;
        int size = limit != null ? Math.min(Math.max(limit, 1), 200) : 20;
        long unreadCount = notificationRepository.countUnreadForUser(ROLE, waiterId);

        List<PushNotificationDto> items;
        long totalCount;
        if (Boolean.TRUE.equals(unreadOnly)) {
            List<Notification> unread = notificationRepository.findUnreadForUser(ROLE, waiterId);
            totalCount = unread.size();
            int from = Math.min(p * size, unread.size());
            int to = Math.min(from + size, unread.size());
            items = unread.subList(from, to).stream().map(this::toDto).toList();
        } else {
            Pageable pageable = PageRequest.of(p, size);
            Page<Notification> pageResult = notificationRepository.findAllForUser(ROLE, waiterId, pageable);
            totalCount = pageResult.getTotalElements();
            items = pageResult.getContent().stream().map(this::toDto).toList();
        }

        return WaiterNotificationListResponse.builder()
                .notifications(items)
                .unreadCount(unreadCount)
                .totalCount(totalCount)
                .build();
    }

    @Transactional(readOnly = true)
    public PushNotificationDto getOne(Long waiterId, Long id) {
        return toDto(loadForWaiter(waiterId, id));
    }

    @Transactional
    public PushNotificationDto markRead(Long waiterId, Long id) {
        Notification n = loadForWaiter(waiterId, id);
        n.markAsRead();
        return toDto(notificationRepository.save(n));
    }

    @Transactional
    public void markAllRead(Long waiterId) {
        notificationRepository.markAllAsReadForUser(ROLE, waiterId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long waiterId) {
        return notificationRepository.countUnreadForUser(ROLE, waiterId);
    }

    // ---- Integration point: create a waiter notification and push it ----

    /**
     * Persist a waiter notification and fire an Expo push to the waiter's
     * devices. Call this from business events (order ready, table needs
     * attention, shift reminder, ...) to notify a waiter.
     */
    @Transactional
    public Notification createAndPush(Long waiterId, NotificationType type, String title,
                                      String body, Map<String, Object> data) {
        String metadata = null;
        if (data != null && !data.isEmpty()) {
            try {
                metadata = objectMapper.writeValueAsString(data);
            } catch (Exception e) {
                log.warn("Could not serialize notification metadata: {}", e.getMessage());
            }
        }
        Notification notification = Notification.builder()
                .userRole(ROLE)
                .userId(waiterId)
                .type(type)
                .title(title)
                .message(body)
                .metadata(metadata)
                .status(NotificationStatus.UNREAD)
                .build();
        notification = notificationRepository.save(notification);
        expoPushService.sendToWaiter(waiterId, notification, data);
        return notification;
    }

    // ---- helpers ----

    private Notification loadForWaiter(Long waiterId, Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        boolean ownedByWaiter = n.getUserRole() == ROLE
                && (n.getUserId() == null || n.getUserId().equals(waiterId));
        if (!ownedByWaiter) {
            throw new ResourceNotFoundException("Notification not found with id: " + id);
        }
        return n;
    }

    private PushNotificationDto toDto(Notification n) {
        return PushNotificationDto.builder()
                .id(n.getId())
                .type(appType(n.getType()))
                .title(n.getTitle())
                .body(n.getMessage())
                .data(parseMetadata(n.getMetadata()))
                .read(n.getStatus() == NotificationStatus.READ)
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();
    }

    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /** Map a stored NotificationType to the app's taxonomy. */
    private String appType(NotificationType type) {
        if (type == null) {
            return "SYSTEM_ALERT";
        }
        String name = type.name();
        if (APP_TYPES.contains(name)) {
            return name;
        }
        if (name.startsWith("NEW_ORDER")) {
            return "NEW_ORDER";
        }
        if (name.contains("PREPAR")) {
            return "KITCHEN_ALERT";
        }
        // remaining legacy values are all order-lifecycle events
        return "ORDER_STATUS";
    }

    private RegisterDeviceResponse toDeviceResponse(WaiterDevice d) {
        return RegisterDeviceResponse.builder()
                .deviceId(d.getId())
                .token(d.getToken())
                .platform(d.getPlatform())
                .registeredAt(d.getRegisteredAt())
                .build();
    }

    private WaiterNotificationPreferenceDto toPreferenceDto(WaiterNotificationPreference p) {
        return WaiterNotificationPreferenceDto.builder()
                .orderUpdates(p.isOrderUpdates())
                .tableReady(p.isTableReady())
                .kitchenAlerts(p.isKitchenAlerts())
                .newOrders(p.isNewOrders())
                .paymentNotifications(p.isPaymentNotifications())
                .systemAlerts(p.isSystemAlerts())
                .shiftReminders(p.isShiftReminders())
                .soundEnabled(p.isSoundEnabled())
                .vibrationEnabled(p.isVibrationEnabled())
                .quietHoursEnabled(p.isQuietHoursEnabled())
                .quietHoursStart(p.getQuietHoursStart())
                .quietHoursEnd(p.getQuietHoursEnd())
                .build();
    }

    private static WaiterNotificationPreferenceDto defaultPreferences() {
        return WaiterNotificationPreferenceDto.builder()
                .orderUpdates(true).tableReady(true).kitchenAlerts(true).newOrders(true)
                .paymentNotifications(true).systemAlerts(true).shiftReminders(true)
                .soundEnabled(true).vibrationEnabled(true).quietHoursEnabled(false)
                .build();
    }
}
