package com.elcafe.modules.waiter.repository;

import com.elcafe.modules.waiter.entity.WaiterKPIConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaiterKPIConfigRepository extends JpaRepository<WaiterKPIConfig, Long> {

    /**
     * Find all KPI configs for a restaurant
     */
    List<WaiterKPIConfig> findByRestaurantIdAndActiveTrue(Long restaurantId);

    /**
     * Find KPI config for a specific waiter
     */
    Optional<WaiterKPIConfig> findByWaiterIdAndActiveTrue(Long waiterId);

    /**
     * Find restaurant default KPI config (waiter_id is null)
     */
    @Query("SELECT k FROM WaiterKPIConfig k WHERE k.restaurant.id = :restaurantId AND k.waiter IS NULL AND k.active = true")
    Optional<WaiterKPIConfig> findRestaurantDefault(@Param("restaurantId") Long restaurantId);

    /**
     * Get effective KPI config for a waiter (waiter-specific or restaurant default)
     */
    @Query("SELECT k FROM WaiterKPIConfig k WHERE k.active = true AND " +
           "(k.waiter.id = :waiterId OR (k.restaurant.id = :restaurantId AND k.waiter IS NULL)) " +
           "ORDER BY CASE WHEN k.waiter IS NOT NULL THEN 0 ELSE 1 END")
    List<WaiterKPIConfig> findEffectiveConfig(@Param("waiterId") Long waiterId, @Param("restaurantId") Long restaurantId);

    /**
     * Check if waiter has custom KPI config
     */
    boolean existsByWaiterIdAndActiveTrue(Long waiterId);

    /**
     * Find all configs (active and inactive) for a restaurant
     */
    List<WaiterKPIConfig> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
}
