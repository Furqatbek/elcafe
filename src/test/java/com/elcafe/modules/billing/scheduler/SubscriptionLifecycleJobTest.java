package com.elcafe.modules.billing.scheduler;

import com.elcafe.modules.billing.service.BillingService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleJobTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private BillingService billingService;
    @InjectMocks private SubscriptionLifecycleJob job;

    private Restaurant restaurant(Long id) {
        Restaurant r = new Restaurant();
        r.setId(id);
        return r;
    }

    @Test
    void reconcilesEveryRestaurant() {
        when(restaurantRepository.findAll())
                .thenReturn(List.of(restaurant(1L), restaurant(2L), restaurant(3L)));

        job.runOnce();

        verify(billingService, times(3)).reconcile(any(Restaurant.class));
    }

    @Test
    void oneFailureDoesNotStopTheRest() {
        Restaurant r1 = restaurant(1L);
        Restaurant r2 = restaurant(2L);
        Restaurant r3 = restaurant(3L);
        when(restaurantRepository.findAll()).thenReturn(List.of(r1, r2, r3));
        doThrow(new RuntimeException("boom")).when(billingService).reconcile(r2);

        job.runOnce();

        verify(billingService).reconcile(r1);
        verify(billingService).reconcile(r2);
        verify(billingService).reconcile(r3); // still reconciled despite r2 throwing
    }
}
