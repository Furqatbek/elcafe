package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.ValuationSettings;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ValuationSettingsRepository extends JpaRepository<ValuationSettings, Long> {

    /**
     * Find the current active valuation settings for a restaurant
     */
    @Query("SELECT vs FROM ValuationSettings vs WHERE vs.restaurant.id = :restaurantId " +
           "AND vs.isActive = true AND vs.effectiveFrom <= :today " +
           "AND (vs.effectiveTo IS NULL OR vs.effectiveTo >= :today) " +
           "ORDER BY vs.effectiveFrom DESC")
    Optional<ValuationSettings> findCurrentSettings(
            @Param("restaurantId") Long restaurantId,
            @Param("today") LocalDate today);

    /**
     * Find all settings for a restaurant (for history)
     */
    List<ValuationSettings> findByRestaurant_IdOrderByEffectiveFromDesc(Long restaurantId);

    /**
     * Find active settings for a restaurant
     */
    Optional<ValuationSettings> findByRestaurant_IdAndIsActiveTrue(Long restaurantId);

    /**
     * Check if a restaurant has any valuation settings
     */
    boolean existsByRestaurantId(Long restaurantId);

    /**
     * Get the valuation method for a restaurant at a specific date
     */
    @Query("SELECT vs.valuationMethod FROM ValuationSettings vs WHERE vs.restaurant.id = :restaurantId " +
           "AND vs.effectiveFrom <= :date " +
           "AND (vs.effectiveTo IS NULL OR vs.effectiveTo >= :date) " +
           "ORDER BY vs.effectiveFrom DESC")
    Optional<ValuationMethod> findMethodAtDate(
            @Param("restaurantId") Long restaurantId,
            @Param("date") LocalDate date);
}
