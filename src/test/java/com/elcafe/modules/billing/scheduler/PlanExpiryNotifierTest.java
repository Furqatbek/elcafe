package com.elcafe.modules.billing.scheduler;

import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanExpiryNotifierTest {

    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final OwnerNotificationService ownerNotificationService = mock(OwnerNotificationService.class);
    private final PlanExpiryNotifier notifier =
            new PlanExpiryNotifier(restaurantRepository, ownerNotificationService);

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 22);

    private Restaurant expiringOn(LocalDateTime expiresAt) {
        Restaurant r = mock(Restaurant.class);
        when(r.getPlanExpiresAt()).thenReturn(expiresAt);
        return r;
    }

    @Test
    @DisplayName("notifies only the window days (7/3/1/0/-1/-2/-3), skips the rest")
    void notifiesOnlyWindowDays() {
        Restaurant in7 = expiringOn(TODAY.plusDays(7).atTime(10, 0));
        Restaurant today0 = expiringOn(TODAY.atTime(10, 0));
        Restaurant grace1 = expiringOn(TODAY.minusDays(1).atTime(10, 0));   // days = -1
        Restaurant out2 = expiringOn(TODAY.plusDays(2).atTime(10, 0));      // days = 2  → skip
        Restaurant out5 = expiringOn(TODAY.plusDays(5).atTime(10, 0));      // days = 5  → skip
        when(restaurantRepository.findWithPlanExpiringBetween(any(), any()))
                .thenReturn(List.of(in7, today0, grace1, out2, out5));

        notifier.runOnce(TODAY);

        verify(ownerNotificationService).notifyPlanExpiry(in7, 7L);
        verify(ownerNotificationService).notifyPlanExpiry(today0, 0L);
        verify(ownerNotificationService).notifyPlanExpiry(grace1, -1L);
        verify(ownerNotificationService, never()).notifyPlanExpiry(eq(out2), anyLong());
        verify(ownerNotificationService, never()).notifyPlanExpiry(eq(out5), anyLong());
    }

    @Test
    @DisplayName("one restaurant failing does not stop the others")
    void oneFailureDoesNotStopOthers() {
        Restaurant bad = expiringOn(TODAY.atTime(9, 0));               // days = 0
        Restaurant good = expiringOn(TODAY.plusDays(1).atTime(9, 0));  // days = 1
        when(restaurantRepository.findWithPlanExpiringBetween(any(), any()))
                .thenReturn(List.of(bad, good));
        doThrow(new RuntimeException("telegram down"))
                .when(ownerNotificationService).notifyPlanExpiry(bad, 0L);

        notifier.runOnce(TODAY); // must not throw

        verify(ownerNotificationService).notifyPlanExpiry(good, 1L);
    }
}
