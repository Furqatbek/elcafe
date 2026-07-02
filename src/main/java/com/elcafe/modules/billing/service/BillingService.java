package com.elcafe.modules.billing.service;

import com.elcafe.modules.billing.enums.SubscriptionStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Phase 3 scaffolding — the subscription lifecycle engine. Owns how a restaurant's
 * {@link SubscriptionStatus} is derived and kept coherent; the platform console calls
 * {@link #computeStatus} on its mutations and the daily {@code SubscriptionLifecycleJob} calls
 * {@link #reconcile}.
 *
 * <p>No charging happens here: prices are 0 and the only {@link PaymentProvider} is the Noop stub, so
 * "renewal" is dormant until a real provider is wired (Phase 3 proper). What this does today is make
 * the lifecycle state explicit and self-healing:
 * <ul>
 *   <li>{@code active == false} → {@link SubscriptionStatus#SUSPENDED}</li>
 *   <li>past expiry + grace → {@link SubscriptionStatus#EXPIRED}</li>
 *   <li>a live trial → {@link SubscriptionStatus#TRIAL}, otherwise {@link SubscriptionStatus#ACTIVE}</li>
 * </ul>
 * {@link SubscriptionStatus#CANCELLED} is an explicit operator action and is <em>sticky</em> — the
 * automatic {@link #reconcile} never overwrites it (only an explicit reactivate does). {@code PAST_DUE}
 * is never produced yet — it belongs to the billing engine's failed-charge path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final RestaurantRepository restaurantRepository;

    /**
     * The lifecycle status implied by a restaurant's active flag, plan expiry and trial flag right now.
     * Pure and side-effect free; never returns {@code CANCELLED}/{@code PAST_DUE} (those aren't derivable
     * from plan state — they're set explicitly / by the billing engine).
     */
    public SubscriptionStatus computeStatus(Restaurant restaurant, LocalDateTime now) {
        if (!Boolean.TRUE.equals(restaurant.getActive())) {
            return SubscriptionStatus.SUSPENDED;
        }
        LocalDateTime expiresAt = restaurant.getPlanExpiresAt();
        if (expiresAt != null && now.isAfter(expiresAt.plusDays(PlanGateService.GRACE_DAYS))) {
            return SubscriptionStatus.EXPIRED;
        }
        return Boolean.TRUE.equals(restaurant.getIsTrial())
                ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE;
    }

    /**
     * Bring the persisted status in line with the current plan state, saving only if it changed.
     * {@code CANCELLED} is left untouched (sticky) so automatic reconciliation can't silently revive a
     * terminated subscription — that takes an explicit reactivate. Safe to call on every restaurant.
     */
    @Transactional
    public void reconcile(Restaurant restaurant) {
        if (restaurant.getSubscriptionStatus() == SubscriptionStatus.CANCELLED) {
            return;
        }
        SubscriptionStatus derived = computeStatus(restaurant, LocalDateTime.now());
        if (derived != restaurant.getSubscriptionStatus()) {
            SubscriptionStatus previous = restaurant.getSubscriptionStatus();
            restaurant.setSubscriptionStatus(derived);
            restaurantRepository.save(restaurant);
            log.debug("Subscription status reconciled for restaurant {}: {} -> {}",
                    restaurant.getId(), previous, derived);
        }
    }
}
