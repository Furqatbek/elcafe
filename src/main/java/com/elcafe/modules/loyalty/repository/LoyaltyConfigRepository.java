package com.elcafe.modules.loyalty.repository;

import com.elcafe.modules.loyalty.entity.LoyaltyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyConfigRepository extends JpaRepository<LoyaltyConfig, Long> {

    Optional<LoyaltyConfig> findByRestaurant_IdAndEnabled(Long restaurantId, Boolean enabled);

    /**
     * One restaurant's active config.
     *
     * <p>The {@code OR lc.restaurant IS NULL} branch that used to be here was dead for any tenant:
     * {@code LoyaltyConfig} carries {@code @Filter(restaurant_id = :restaurantId)}, which Hibernate ANDs
     * onto this query, and no NULL row can satisfy {@code restaurant_id = 4}. It survived only for
     * background jobs, which run unfiltered — so the "global" config governed the scheduler and nothing
     * else. V185 removed the global rows and made the column NOT NULL; this query no longer pretends.
     */
    @Query("SELECT lc FROM LoyaltyConfig lc WHERE lc.restaurant.id = :restaurantId AND lc.enabled = true")
    Optional<LoyaltyConfig> findActiveConfigForRestaurant(@Param("restaurantId") Long restaurantId);

    /**
     * Every restaurant that has switched loyalty on, for the background sweeps.
     *
     * <p>Jobs run outside a request, so no tenant filter is enabled and this genuinely sees them all —
     * which is what a platform-wide sweep needs. It then applies each restaurant's <em>own</em>
     * settings rather than one shared number.
     */
    @Query("SELECT lc FROM LoyaltyConfig lc WHERE lc.restaurant IS NOT NULL AND lc.enabled = true")
    List<LoyaltyConfig> findAllEnabledPerRestaurantConfigs();
}
