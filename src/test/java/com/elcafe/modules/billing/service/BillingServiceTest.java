package com.elcafe.modules.billing.service;

import com.elcafe.modules.billing.enums.SubscriptionStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private BillingService service;

    private final LocalDateTime now = LocalDateTime.of(2026, 7, 1, 12, 0);

    private Restaurant restaurant(boolean active, boolean trial, LocalDateTime expiresAt, SubscriptionStatus status) {
        Restaurant r = new Restaurant();
        r.setId(1L);
        r.setActive(active);
        r.setIsTrial(trial);
        r.setPlanExpiresAt(expiresAt);
        r.setSubscriptionStatus(status);
        return r;
    }

    // ---- computeStatus (pure derivation) ----

    @Test
    void inactiveIsSuspended() {
        assertThat(service.computeStatus(restaurant(false, false, null, SubscriptionStatus.ACTIVE), now))
                .isEqualTo(SubscriptionStatus.SUSPENDED);
    }

    @Test
    void pastGraceIsExpired() {
        Restaurant r = restaurant(true, false, now.minusDays(5), SubscriptionStatus.ACTIVE); // grace is 3
        assertThat(service.computeStatus(r, now)).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void withinGraceStaysActive() {
        Restaurant r = restaurant(true, false, now.minusDays(1), SubscriptionStatus.ACTIVE);
        assertThat(service.computeStatus(r, now)).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void liveTrialIsTrial() {
        Restaurant r = restaurant(true, true, now.plusDays(10), SubscriptionStatus.ACTIVE);
        assertThat(service.computeStatus(r, now)).isEqualTo(SubscriptionStatus.TRIAL);
    }

    @Test
    void activeNoExpiryIsActive() {
        assertThat(service.computeStatus(restaurant(true, false, null, SubscriptionStatus.ACTIVE), now))
                .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    // ---- reconcile (persist derived; CANCELLED sticky) ----

    @Test
    void reconcilePersistsDerivedWhenChanged() {
        Restaurant r = restaurant(false, false, null, SubscriptionStatus.ACTIVE); // stale: should be SUSPENDED
        service.reconcile(r);
        assertThat(r.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        verify(restaurantRepository).save(r);
    }

    @Test
    void reconcilePreservesCancelled() {
        Restaurant r = restaurant(true, false, null, SubscriptionStatus.CANCELLED); // looks active, but cancelled
        service.reconcile(r);
        assertThat(r.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(restaurantRepository, never()).save(r);
    }

    @Test
    void reconcileNoSaveWhenUnchanged() {
        Restaurant r = restaurant(true, false, null, SubscriptionStatus.ACTIVE); // already correct
        service.reconcile(r);
        assertThat(r.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(restaurantRepository, never()).save(r);
    }
}
