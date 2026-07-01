package com.elcafe.modules.billing.service;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionAccessServiceTest {

    private static final String KEY = "subscription:suspended:1";

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private SubscriptionAccessService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private Restaurant restaurant(boolean active) {
        Restaurant r = new Restaurant();
        r.setId(1L);
        r.setActive(active);
        return r;
    }

    @Test
    void cacheMiss_inactive_isSuspended_andCaches() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant(false)));

        assertThat(service.isSuspended(1L)).isTrue();
        verify(valueOps).set(eq(KEY), eq("true"), any(Duration.class));
    }

    @Test
    void cacheMiss_active_notSuspended_andCaches() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant(true)));

        assertThat(service.isSuspended(1L)).isFalse();
        verify(valueOps).set(eq(KEY), eq("false"), any(Duration.class));
    }

    @Test
    void cacheHit_servesFromRedisWithoutHittingDb() {
        when(valueOps.get(KEY)).thenReturn("true");

        assertThat(service.isSuspended(1L)).isTrue();
        verify(restaurantRepository, never()).findById(any());
    }

    @Test
    void nullIdIsNotSuspended() {
        assertThat(service.isSuspended(null)).isFalse();
        verify(restaurantRepository, never()).findById(any());
    }

    @Test
    void missingRestaurantFailsOpen() {
        when(valueOps.get("subscription:suspended:9")).thenReturn(null);
        when(restaurantRepository.findById(9L)).thenReturn(Optional.empty());

        assertThat(service.isSuspended(9L)).isFalse();
    }

    @Test
    void redisDownFallsBackToDb() {
        when(valueOps.get(KEY)).thenThrow(new RuntimeException("connection refused"));
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant(false)));

        assertThat(service.isSuspended(1L)).isTrue(); // answered from the DB, never locked out
    }

    @Test
    void invalidateDeletesTheKey() {
        service.invalidate(1L);
        verify(redisTemplate).delete(KEY);
    }
}
