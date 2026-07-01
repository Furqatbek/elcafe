package com.elcafe.modules.billing.service;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionAccessServiceTest {

    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private SubscriptionAccessService service;

    private Restaurant restaurant(boolean active) {
        Restaurant r = new Restaurant();
        r.setId(1L);
        r.setActive(active);
        return r;
    }

    @Test
    void suspendedWhenInactive() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant(false)));
        assertThat(service.isSuspended(1L)).isTrue();
    }

    @Test
    void notSuspendedWhenActive() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant(true)));
        assertThat(service.isSuspended(1L)).isFalse();
    }

    @Test
    void nullIdIsNotSuspended() {
        assertThat(service.isSuspended(null)).isFalse();
        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void missingRestaurantFailsOpen() {
        when(restaurantRepository.findById(9L)).thenReturn(Optional.empty());
        assertThat(service.isSuspended(9L)).isFalse();
    }

    @Test
    void cachesWithinTtlThenInvalidateReloads() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant(false)));

        assertThat(service.isSuspended(1L)).isTrue();
        assertThat(service.isSuspended(1L)).isTrue();
        verify(restaurantRepository, times(1)).findById(1L); // second read served from cache

        service.invalidate(1L);
        assertThat(service.isSuspended(1L)).isTrue();
        verify(restaurantRepository, times(2)).findById(1L); // reloaded after invalidate
    }
}
