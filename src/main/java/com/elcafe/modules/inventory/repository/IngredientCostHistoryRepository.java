package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.IngredientCostHistory;
import com.elcafe.modules.inventory.enums.CostChangeReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientCostHistoryRepository extends JpaRepository<IngredientCostHistory, Long> {

    /**
     * Find cost history for an ingredient, ordered by date descending
     */
    List<IngredientCostHistory> findByIngredientIdOrderByEffectiveFromDesc(Long ingredientId);

    /**
     * Find cost history with pagination
     */
    Page<IngredientCostHistory> findByIngredientId(Long ingredientId, Pageable pageable);

    /**
     * Get the cost at a specific point in time
     */
    @Query("SELECT ich FROM IngredientCostHistory ich WHERE ich.ingredient.id = :ingredientId " +
           "AND ich.effectiveFrom <= :dateTime " +
           "ORDER BY ich.effectiveFrom DESC")
    List<IngredientCostHistory> findCostAtDateTime(
            @Param("ingredientId") Long ingredientId,
            @Param("dateTime") LocalDateTime dateTime);

    /**
     * Get the most recent cost change
     */
    Optional<IngredientCostHistory> findFirstByIngredientIdOrderByEffectiveFromDesc(Long ingredientId);

    /**
     * Find cost changes within a date range
     */
    @Query("SELECT ich FROM IngredientCostHistory ich WHERE ich.ingredient.id = :ingredientId " +
           "AND ich.effectiveFrom >= :startDate AND ich.effectiveFrom <= :endDate " +
           "ORDER BY ich.effectiveFrom DESC")
    List<IngredientCostHistory> findByIngredientIdAndDateRange(
            @Param("ingredientId") Long ingredientId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find cost changes by reason
     */
    List<IngredientCostHistory> findByIngredientIdAndReasonOrderByEffectiveFromDesc(
            Long ingredientId, CostChangeReason reason);

    /**
     * Find all cost changes for a restaurant's ingredients in a date range
     */
    @Query("SELECT ich FROM IngredientCostHistory ich WHERE ich.ingredient.restaurant.id = :restaurantId " +
           "AND ich.effectiveFrom >= :startDate AND ich.effectiveFrom <= :endDate " +
           "ORDER BY ich.effectiveFrom DESC")
    List<IngredientCostHistory> findByRestaurantIdAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get average cost for an ingredient over a period
     */
    @Query("SELECT AVG(ich.newCost) FROM IngredientCostHistory ich WHERE ich.ingredient.id = :ingredientId " +
           "AND ich.effectiveFrom >= :startDate AND ich.effectiveFrom <= :endDate")
    Optional<BigDecimal> getAverageCostInPeriod(
            @Param("ingredientId") Long ingredientId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count cost changes for an ingredient
     */
    long countByIngredientId(Long ingredientId);
}
