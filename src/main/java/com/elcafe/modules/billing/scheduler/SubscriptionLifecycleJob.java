package com.elcafe.modules.billing.scheduler;

import com.elcafe.modules.billing.enums.SubscriptionStatus;
import com.elcafe.modules.billing.service.BillingService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 3 scaffolding — the daily subscription lifecycle tick. Reconciles every restaurant's
 * {@link SubscriptionStatus} against its plan/expiry/active state so the persisted status self-heals
 * (a trial that lapsed overnight becomes {@code EXPIRED}, etc.) without waiting for an operator action.
 * Runs at 08:30, before the 09:00 {@code PlanExpiryNotifier}.
 *
 * <p>This is where recurring billing would live once a real payment provider is wired — for each
 * subscription due, charge via the provider and extend or move to {@code PAST_DUE}. Today there is no
 * charging (prices are 0, only the Noop provider), so the tick is pure reconciliation. One restaurant
 * failing never stops the rest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionLifecycleJob {

    private final RestaurantRepository restaurantRepository;
    private final BillingService billingService;

    @Scheduled(cron = "0 30 8 * * *")
    @SchedulerLock(name = "subscription-lifecycle-daily", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    public void runDaily() {
        runOnce();
    }

    /** Reconcile every restaurant's subscription status. Package-visible so tests can invoke it directly. */
    void runOnce() {
        List<Restaurant> all = restaurantRepository.findAll();
        int failures = 0;
        for (Restaurant restaurant : all) {
            try {
                billingService.reconcile(restaurant);
            } catch (Exception e) {
                failures++;
                log.warn("Failed to reconcile subscription status for restaurant {}: {}",
                        restaurant.getId(), e.getMessage());
            }
        }
        log.info("Subscription lifecycle tick: {} restaurants reconciled ({} failures)", all.size(), failures);
    }
}
