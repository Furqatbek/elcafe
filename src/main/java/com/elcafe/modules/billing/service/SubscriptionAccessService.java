package com.elcafe.modules.billing.service;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Entitlement lookup for the Phase 2 access gate: is a tenant currently cut off? Under the no-payments
 * scope, "cut off" means a SUPER_ADMIN has suspended the restaurant ({@code Restaurant.active == false})
 * via the platform console. Expired-but-not-suspended plans are handled by read-only mode
 * ({@code PlanWriteGuardInterceptor}), not here.
 *
 * <p>The gate runs on every staff request, so the flag is cached in Redis (short TTL, keyed by
 * restaurant id) — shared across instances so {@link #invalidate(Long)} on suspend/reactivate takes
 * effect everywhere immediately, not just on the node that made the change. Redis failures degrade
 * gracefully: reads fall back to the DB and never lock a tenant out (same pattern as
 * {@code PaymentIdempotencyService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionAccessService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final String KEY_PREFIX = "subscription:suspended:";

    private final RestaurantRepository restaurantRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Whether the tenant is currently suspended. Fail-open: a null id or a missing restaurant is treated
     * as not-suspended so a lookup miss can never lock a tenant out.
     */
    @Transactional(readOnly = true)
    public boolean isSuspended(Long restaurantId) {
        if (restaurantId == null) {
            return false;
        }
        String key = KEY_PREFIX + restaurantId;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Boolean.parseBoolean(cached);
            }
        } catch (Exception e) {
            // Redis down → skip the cache and answer straight from the DB (don't lock anyone out).
            log.warn("Redis unavailable for subscription-access read of {}, using DB: {}", restaurantId, e.getMessage());
            return loadFromDb(restaurantId);
        }
        boolean suspended = loadFromDb(restaurantId);
        try {
            redisTemplate.opsForValue().set(key, Boolean.toString(suspended), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis unavailable for subscription-access write of {}: {}", restaurantId, e.getMessage());
        }
        return suspended;
    }

    private boolean loadFromDb(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .map(Restaurant::getActive)
                .map(active -> !Boolean.TRUE.equals(active))
                .orElse(false);
    }

    public void invalidate(Long restaurantId) {
        if (restaurantId == null) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + restaurantId);
        } catch (Exception e) {
            log.warn("Redis unavailable for subscription-access invalidate of {}: {}", restaurantId, e.getMessage());
        }
    }
}
