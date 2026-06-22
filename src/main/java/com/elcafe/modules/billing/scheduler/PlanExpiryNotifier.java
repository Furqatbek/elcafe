package com.elcafe.modules.billing.scheduler;

import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Daily owner reminders as a subscription approaches and passes expiry (mini-phase A6). Modeled on
 * {@code LowStockAlertScheduler}: runs at 09:00 and Telegrams a restaurant's owner subscribers when
 * the plan expires in 7 / 3 / 1 / 0 days, or 1–3 days into the grace window.
 *
 * <p>As a background job it runs with no request {@code TenantContext}, so the §3.4 tenant filter is
 * inert and the cross-tenant query is intentional (like other schedulers). Inert in practice until
 * restaurants carry a {@code plan_expires_at}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanExpiryNotifier {

    /** daysUntilExpiry values that fire a reminder: pre-expiry 7/3/1/today + grace days 1/2/3 after. */
    private static final Set<Long> NOTIFY_WINDOWS = Set.of(7L, 3L, 1L, 0L, -1L, -2L, -3L);
    private static final long LOOKBACK_DAYS = 3;
    private static final long LOOKAHEAD_DAYS = 7;

    private final RestaurantRepository restaurantRepository;
    private final OwnerNotificationService ownerNotificationService;

    @Scheduled(cron = "0 0 9 * * *")
    public void notifyExpiringPlans() {
        runOnce(LocalDate.now());
    }

    /** Package-visible so a test can drive it against a fixed "today". */
    void runOnce(LocalDate today) {
        LocalDateTime start = today.minusDays(LOOKBACK_DAYS).atStartOfDay();
        LocalDateTime end = today.plusDays(LOOKAHEAD_DAYS).atTime(LocalTime.MAX);
        List<Restaurant> candidates = restaurantRepository.findWithPlanExpiringBetween(start, end);

        int notified = 0;
        for (Restaurant restaurant : candidates) {
            try {
                if (restaurant.getPlanExpiresAt() == null) {
                    continue;
                }
                long daysUntilExpiry = ChronoUnit.DAYS.between(today, restaurant.getPlanExpiresAt().toLocalDate());
                if (!NOTIFY_WINDOWS.contains(daysUntilExpiry)) {
                    continue;
                }
                ownerNotificationService.notifyPlanExpiry(restaurant, daysUntilExpiry);
                notified++;
            } catch (Exception e) {
                log.error("Plan-expiry notification failed for restaurant {}: {}",
                        restaurant.getId(), e.getMessage());
            }
        }
        log.info("Plan-expiry notifier: {} candidate(s) in window, {} notified ({})",
                candidates.size(), notified, today);
    }
}
