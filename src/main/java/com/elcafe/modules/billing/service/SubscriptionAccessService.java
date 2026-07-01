package com.elcafe.modules.billing.service;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entitlement lookup for the Phase 2 access gate: is a tenant currently cut off? Under the no-payments
 * scope, "cut off" means a SUPER_ADMIN has suspended the restaurant ({@code Restaurant.active == false})
 * via the platform console. Expired-but-not-suspended plans are handled by read-only mode
 * ({@code PlanWriteGuardInterceptor}), not here.
 *
 * <p>The gate runs on every staff request, so results are served from a short-TTL in-process cache
 * (like {@link PlanGateService}); {@link #invalidate(Long)} is called on suspend/reactivate so a status
 * change takes effect immediately rather than waiting out the TTL.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionAccessService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final RestaurantRepository restaurantRepository;

    private record CachedFlag(boolean suspended, Instant loadedAt) {}

    private final Map<Long, CachedFlag> cache = new ConcurrentHashMap<>();

    /**
     * Whether the tenant is currently suspended. Fail-open: a null id or a missing restaurant is treated
     * as not-suspended so a lookup miss can never lock a tenant out.
     */
    @Transactional(readOnly = true)
    public boolean isSuspended(Long restaurantId) {
        if (restaurantId == null) {
            return false;
        }
        CachedFlag cached = cache.get(restaurantId);
        if (cached != null && Duration.between(cached.loadedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return cached.suspended();
        }
        boolean suspended = restaurantRepository.findById(restaurantId)
                .map(Restaurant::getActive)
                .map(active -> !Boolean.TRUE.equals(active))
                .orElse(false);
        cache.put(restaurantId, new CachedFlag(suspended, Instant.now()));
        return suspended;
    }

    public void invalidate(Long restaurantId) {
        if (restaurantId != null) {
            cache.remove(restaurantId);
        }
    }
}
