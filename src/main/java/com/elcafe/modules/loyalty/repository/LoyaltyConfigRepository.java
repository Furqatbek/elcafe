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

    @Query("SELECT lc FROM LoyaltyConfig lc WHERE " +
           "(lc.restaurant.id = :restaurantId OR lc.restaurant IS NULL) AND lc.enabled = true " +
           "ORDER BY lc.restaurant.id DESC NULLS LAST LIMIT 1")
    Optional<LoyaltyConfig> findActiveConfigForRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT lc FROM LoyaltyConfig lc WHERE lc.restaurant IS NULL AND lc.enabled = true")
    Optional<LoyaltyConfig> findGlobalConfig();

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
