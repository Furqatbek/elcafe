package com.elcafe.modules.notification.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.notification.dto.waiter.PushNotificationDto;
import com.elcafe.modules.notification.dto.waiter.RegisterDeviceRequest;
import com.elcafe.modules.notification.dto.waiter.RegisterDeviceResponse;
import com.elcafe.modules.notification.dto.waiter.WaiterNotificationListResponse;
import com.elcafe.modules.notification.dto.waiter.WaiterNotificationPreferenceDto;
import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.entity.WaiterDevice;
import com.elcafe.modules.notification.entity.WaiterNotificationPreference;
import com.elcafe.modules.notification.enums.DevicePlatform;
import com.elcafe.modules.notification.enums.NotificationStatus;
import com.elcafe.modules.notification.enums.NotificationType;
import com.elcafe.modules.notification.enums.UserRole;
import com.elcafe.modules.notification.repository.NotificationRepository;
import com.elcafe.modules.notification.repository.WaiterDeviceRepository;
import com.elcafe.modules.notification.repository.WaiterNotificationPreferenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaiterNotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private WaiterDeviceRepository deviceRepository;
    @Mock private WaiterNotificationPreferenceRepository preferenceRepository;
    @Mock private ExpoPushService expoPushService;

    private WaiterNotificationService service;

    @BeforeEach
    void setUp() {
        service = new WaiterNotificationService(
                notificationRepository, deviceRepository, preferenceRepository,
                expoPushService, new ObjectMapper());
    }

    @Test @DisplayName("registerDevice upserts by token (existing token is updated, not duplicated)")
    void registerDevice_upsert() {
        WaiterDevice existing = WaiterDevice.builder().id(3L).waiterId(1L)
                .token("ExponentPushToken[x]").platform(DevicePlatform.IOS).build();
        when(deviceRepository.findByToken("ExponentPushToken[x]")).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RegisterDeviceRequest req = new RegisterDeviceRequest();
        req.setToken("ExponentPushToken[x]");
        req.setPlatform(DevicePlatform.ANDROID);

        RegisterDeviceResponse resp = service.registerDevice(5L, req);

        assertThat(resp.getToken()).isEqualTo("ExponentPushToken[x]");
        assertThat(existing.getWaiterId()).isEqualTo(5L);            // reassigned to caller
        assertThat(existing.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
    }

    @Test @DisplayName("getPreferences returns all-on defaults when the waiter has no row")
    void getPreferences_defaults() {
        when(preferenceRepository.findByWaiterId(5L)).thenReturn(Optional.empty());
        WaiterNotificationPreferenceDto dto = service.getPreferences(5L);
        assertThat(dto.getOrderUpdates()).isTrue();
        assertThat(dto.getQuietHoursEnabled()).isFalse();
    }

    @Test @DisplayName("updatePreferences applies only non-null fields (partial)")
    void updatePreferences_partial() {
        WaiterNotificationPreference pref = WaiterNotificationPreference.builder().id(1L).waiterId(5L).build();
        when(preferenceRepository.findByWaiterId(5L)).thenReturn(Optional.of(pref));
        when(preferenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WaiterNotificationPreferenceDto patch = WaiterNotificationPreferenceDto.builder()
                .soundEnabled(false).build();
        service.updatePreferences(5L, patch);

        assertThat(pref.isSoundEnabled()).isFalse();   // changed
        assertThat(pref.isOrderUpdates()).isTrue();     // untouched default
    }

    @Test @DisplayName("list maps legacy notification types to the app taxonomy and reports counts")
    void list_mapsTypesAndCounts() {
        Notification n = Notification.builder()
                .id(7L).userRole(UserRole.WAITER).userId(5L)
                .type(NotificationType.ORDER_READY).title("Ready").message("Order up")
                .status(NotificationStatus.UNREAD).build();
        when(notificationRepository.countUnreadForUser(UserRole.WAITER, 5L)).thenReturn(1L);
        when(notificationRepository.findAllForUser(eq(UserRole.WAITER), eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(n)));

        WaiterNotificationListResponse resp = service.list(5L, 0, 20, false);

        assertThat(resp.getUnreadCount()).isEqualTo(1);
        assertThat(resp.getTotalCount()).isEqualTo(1);
        assertThat(resp.getNotifications()).hasSize(1);
        // ORDER_READY is a legacy order-lifecycle type → maps to ORDER_STATUS
        assertThat(resp.getNotifications().get(0).getType()).isEqualTo("ORDER_STATUS");
        assertThat(resp.getNotifications().get(0).isRead()).isFalse();
    }

    @Test @DisplayName("markRead marks a waiter's own notification read")
    void markRead_ownNotification() {
        Notification n = Notification.builder()
                .id(9L).userRole(UserRole.WAITER).userId(5L)
                .type(NotificationType.NEW_ORDER).title("t").message("b")
                .status(NotificationStatus.UNREAD).build();
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PushNotificationDto dto = service.markRead(5L, 9L);

        assertThat(dto.isRead()).isTrue();
        assertThat(dto.getType()).isEqualTo("NEW_ORDER");
    }

    @Test @DisplayName("markRead refuses another waiter's notification (404, not cross-read)")
    void markRead_otherWaiter_rejected() {
        Notification n = Notification.builder()
                .id(9L).userRole(UserRole.WAITER).userId(99L)
                .type(NotificationType.NEW_ORDER).title("t").message("b")
                .status(NotificationStatus.UNREAD).build();
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.markRead(5L, 9L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test @DisplayName("createAndPush persists the notification and fires an Expo push")
    void createAndPush_persistsAndPushes() {
        when(notificationRepository.save(any())).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            n.setId(11L);
            return n;
        });

        service.createAndPush(5L, NotificationType.NEW_ORDER, "New order", "Table 4",
                java.util.Map.of("orderId", 42));

        verify(notificationRepository).save(any(Notification.class));
        verify(expoPushService).sendToWaiter(eq(5L), any(Notification.class), any());
    }
}
