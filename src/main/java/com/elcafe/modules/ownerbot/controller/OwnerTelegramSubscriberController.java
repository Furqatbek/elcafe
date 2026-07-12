package com.elcafe.modules.ownerbot.controller;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.ownerbot.entity.OwnerNotificationSettings;
import com.elcafe.modules.ownerbot.entity.OwnerTelegramSubscriber;
import com.elcafe.modules.ownerbot.repository.OwnerNotificationLogRepository;
import com.elcafe.modules.ownerbot.repository.OwnerNotificationSettingsRepository;
import com.elcafe.modules.ownerbot.repository.OwnerTelegramSubscriberRepository;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Admin endpoints for managing Telegram owner-bot subscribers. Lets
 * operators see who's currently receiving notifications, flip per-event
 * toggles, deactivate or hard-delete rows. Previously the only way to
 * manage subscribers was direct SQL.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/telegram/owner-subscribers")
@RequiredArgsConstructor
public class OwnerTelegramSubscriberController {

    private final OwnerTelegramSubscriberRepository subscriberRepository;
    private final OwnerNotificationSettingsRepository settingsRepository;
    private final OwnerNotificationLogRepository logRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<List<SubscriberSummary>>> list(
            @RequestParam(required = false) Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        // Return both active and inactive rows so an admin can re-activate
        // a previously-disabled subscriber. The per-restaurant filter is
        // applied in-memory rather than via the active-only repo helper.
        List<OwnerTelegramSubscriber> rows = subscriberRepository.findAll().stream()
                .filter(s -> restaurantId == null
                        || (s.getRestaurant() != null && restaurantId.equals(s.getRestaurant().getId())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Subscribers",
                rows.stream().map(SubscriberSummary::from).toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<SubscriberDetail>> getById(@PathVariable Long id) {
        OwnerTelegramSubscriber s = subscriberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscriber not found"));
        return ResponseEntity.ok(ApiResponse.success("Subscriber", SubscriberDetail.from(s)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<SubscriberDetail>> update(
            @PathVariable Long id,
            @RequestBody UpdateRequest req) {
        OwnerTelegramSubscriber s = subscriberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscriber not found"));

        // Subscriber-level toggles. null = leave unchanged.
        if (req.isActive() != null) s.setIsActive(req.isActive());
        if (req.role() != null) s.setRole(req.role().isBlank() ? null : req.role());

        // Notification settings: create-on-demand if missing so admins
        // can flip flags for subscribers that joined before the settings
        // row existed.
        if (req.settings() != null) {
            OwnerNotificationSettings settings = s.getNotificationSettings();
            if (settings == null) {
                settings = OwnerNotificationSettings.builder().subscriber(s).build();
                s.setNotificationSettings(settings);
            }
            applySettings(settings, req.settings());
            settingsRepository.save(settings);
        }

        OwnerTelegramSubscriber saved = subscriberRepository.save(s);
        return ResponseEntity.ok(ApiResponse.success("Subscriber updated", SubscriberDetail.from(saved)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        OwnerTelegramSubscriber s = subscriberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscriber not found"));
        // Drop notification log rows first — they reference the
        // subscriber and are otherwise orphaned.
        logRepository.deleteBySubscriberId(s.getId());
        subscriberRepository.delete(s);
        log.info("Deleted owner-bot subscriber {}", id);
        return ResponseEntity.ok(ApiResponse.success("Subscriber deleted", null));
    }

    private static void applySettings(OwnerNotificationSettings s, SettingsRequest req) {
        if (req.notifyNewOrder() != null) s.setNotifyNewOrder(req.notifyNewOrder());
        if (req.notifyNewReservation() != null) s.setNotifyNewReservation(req.notifyNewReservation());
        if (req.notifyLowStock() != null) s.setNotifyLowStock(req.notifyLowStock());
        if (req.notifyCustomerReview() != null) s.setNotifyCustomerReview(req.notifyCustomerReview());
        if (req.notifyDailyReport() != null) s.setNotifyDailyReport(req.notifyDailyReport());
        if (req.notifyCriticalAlerts() != null) s.setNotifyCriticalAlerts(req.notifyCriticalAlerts());
        if (req.notifyOrderCancelled() != null) s.setNotifyOrderCancelled(req.notifyOrderCancelled());
        if (req.notifyReservationCancelled() != null) s.setNotifyReservationCancelled(req.notifyReservationCancelled());
        if (req.quietHoursEnabled() != null) s.setQuietHoursEnabled(req.quietHoursEnabled());
        if (req.quietHoursStart() != null) s.setQuietHoursStart(LocalTime.parse(req.quietHoursStart()));
        if (req.quietHoursEnd() != null) s.setQuietHoursEnd(LocalTime.parse(req.quietHoursEnd()));
        if (req.receiveDailySummary() != null) s.setReceiveDailySummary(req.receiveDailySummary());
        if (req.minOrderAmountNotify() != null) s.setMinOrderAmountNotify(req.minOrderAmountNotify());
        if (req.lowStockThreshold() != null) s.setLowStockThreshold(req.lowStockThreshold());
    }

    // -------------------- DTOs --------------------

    public record SubscriberSummary(
            Long id,
            Long telegramUserId,
            String username,
            String displayName,
            String role,
            Long restaurantId,
            String restaurantName,
            Boolean isActive,
            Boolean isVerified,
            LocalDateTime subscribedAt,
            LocalDateTime lastInteractionAt
    ) {
        static SubscriberSummary from(OwnerTelegramSubscriber s) {
            return new SubscriberSummary(
                    s.getId(),
                    s.getTelegramUserId(),
                    s.getUsername(),
                    s.getDisplayName(),
                    s.getRole(),
                    s.getRestaurant() != null ? s.getRestaurant().getId() : null,
                    s.getRestaurant() != null ? s.getRestaurant().getName() : null,
                    s.getIsActive(),
                    s.getIsVerified(),
                    s.getSubscribedAt(),
                    s.getLastInteractionAt());
        }
    }

    public record SubscriberDetail(
            Long id,
            Long telegramUserId,
            String username,
            String firstName,
            String lastName,
            String displayName,
            String role,
            Long restaurantId,
            String restaurantName,
            Boolean isActive,
            Boolean isVerified,
            LocalDateTime subscribedAt,
            LocalDateTime lastInteractionAt,
            SettingsView settings
    ) {
        static SubscriberDetail from(OwnerTelegramSubscriber s) {
            return new SubscriberDetail(
                    s.getId(),
                    s.getTelegramUserId(),
                    s.getUsername(),
                    s.getFirstName(),
                    s.getLastName(),
                    s.getDisplayName(),
                    s.getRole(),
                    s.getRestaurant() != null ? s.getRestaurant().getId() : null,
                    s.getRestaurant() != null ? s.getRestaurant().getName() : null,
                    s.getIsActive(),
                    s.getIsVerified(),
                    s.getSubscribedAt(),
                    s.getLastInteractionAt(),
                    SettingsView.from(s.getNotificationSettings()));
        }
    }

    public record SettingsView(
            Boolean notifyNewOrder,
            Boolean notifyNewReservation,
            Boolean notifyLowStock,
            Boolean notifyCustomerReview,
            Boolean notifyDailyReport,
            Boolean notifyCriticalAlerts,
            Boolean notifyOrderCancelled,
            Boolean notifyReservationCancelled,
            Boolean quietHoursEnabled,
            String quietHoursStart,
            String quietHoursEnd,
            Boolean receiveDailySummary,
            BigDecimal minOrderAmountNotify,
            Integer lowStockThreshold
    ) {
        static SettingsView from(OwnerNotificationSettings s) {
            if (s == null) {
                // Mirror the entity defaults (every notify* defaults to true)
                // so the UI shows the same enabled state the bot uses when
                // the settings row hasn't been created yet.
                return new SettingsView(
                        true, true, true, true, true, true, true, true,
                        false, "23:00", "07:00", true,
                        BigDecimal.ZERO, 10);
            }
            return new SettingsView(
                    s.getNotifyNewOrder(),
                    s.getNotifyNewReservation(),
                    s.getNotifyLowStock(),
                    s.getNotifyCustomerReview(),
                    s.getNotifyDailyReport(),
                    s.getNotifyCriticalAlerts(),
                    s.getNotifyOrderCancelled(),
                    s.getNotifyReservationCancelled(),
                    s.getQuietHoursEnabled(),
                    s.getQuietHoursStart() != null ? s.getQuietHoursStart().toString() : null,
                    s.getQuietHoursEnd() != null ? s.getQuietHoursEnd().toString() : null,
                    s.getReceiveDailySummary(),
                    s.getMinOrderAmountNotify(),
                    s.getLowStockThreshold());
        }
    }

    /**
     * PATCH body. Every field is optional — null means "leave unchanged".
     */
    public record UpdateRequest(
            Boolean isActive,
            String role,
            SettingsRequest settings
    ) {}

    public record SettingsRequest(
            Boolean notifyNewOrder,
            Boolean notifyNewReservation,
            Boolean notifyLowStock,
            Boolean notifyCustomerReview,
            Boolean notifyDailyReport,
            Boolean notifyCriticalAlerts,
            Boolean notifyOrderCancelled,
            Boolean notifyReservationCancelled,
            Boolean quietHoursEnabled,
            String quietHoursStart,   // "HH:mm"
            String quietHoursEnd,     // "HH:mm"
            Boolean receiveDailySummary,
            BigDecimal minOrderAmountNotify,
            Integer lowStockThreshold
    ) {}
}
