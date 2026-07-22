package com.elcafe.modules.loyalty.repository;

import com.elcafe.modules.loyalty.entity.LoyaltyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
