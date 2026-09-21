package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.BatchConsumption;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BatchConsumptionRepository extends JpaRepository<BatchConsumption, Long> {

    /**
     * Find all consumptions for a batch
     */
    List<BatchConsumption> findByBatchIdOrderByConsumedAtDesc(Long batchId);

    /**
     * Find all consumptions for an ingredient
     */
    List<BatchConsumption> findByIngredientIdOrderByConsumedAtDesc(Long ingredientId);

    /**
     * Find consumptions with pagination
     */
    Page<BatchConsumption> findByIngredientId(Long ingredientId, Pageable pageable);

    /**
     * Find all consumptions for an order (for COGS calculation)
     */
    List<BatchConsumption> findByOrderId(Long orderId);

    /**
     * Calculate total COGS for an order
     */
    @Query("SELECT COALESCE(SUM(bc.totalCost), 0) FROM BatchConsumption bc WHERE bc.orderId = :orderId")
    BigDecimal calculateOrderCOGS(@Param("orderId") Long orderId);

    /**
     * Calculate total COGS for a date range (for a restaurant)
     */
    @Query("SELECT COALESCE(SUM(bc.totalCost), 0) FROM BatchConsumption bc " +
           "WHERE bc.ingredient.restaurant.id = :restaurantId " +
           "AND bc.consumedAt >= :startDate AND bc.consumedAt <= :endDate")
    BigDecimal calculateTotalCOGS(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get consumption grouped by ingredient for a date range
     */
    @Query("SELECT bc.ingredient.id, bc.ingredient.name, SUM(bc.quantity), SUM(bc.totalCost) " +
           "FROM BatchConsumption bc WHERE bc.ingredient.restaurant.id = :restaurantId " +
           "AND bc.consumedAt >= :startDate AND bc.consumedAt <= :endDate " +
           "GROUP BY bc.ingredient.id, bc.ingredient.name " +
           "ORDER BY SUM(bc.totalCost) DESC")
    List<Object[]> getConsumptionByIngredient(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get consumption history for an ingredient in a date range
     */
    @Query("SELECT bc FROM BatchConsumption bc WHERE bc.ingredient.id = :ingredientId " +
           "AND bc.consumedAt >= :startDate AND bc.consumedAt <= :endDate " +
           "ORDER BY bc.consumedAt DESC")
    List<BatchConsumption> findByIngredientIdAndDateRange(
            @Param("ingredientId") Long ingredientId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get total quantity consumed from an ingredient
     */
    @Query("SELECT COALESCE(SUM(bc.quantity), 0) FROM BatchConsumption bc WHERE bc.ingredient.id = :ingredientId")
    BigDecimal getTotalQuantityConsumed(@Param("ingredientId") Long ingredientId);

    /**
     * Get total quantity consumed from an ingredient in a date range
     */
    @Query("SELECT COALESCE(SUM(bc.quantity), 0) FROM BatchConsumption bc " +
           "WHERE bc.ingredient.id = :ingredientId " +
           "AND bc.consumedAt >= :startDate AND bc.consumedAt <= :endDate")
    BigDecimal getQuantityConsumedInPeriod(
            @Param("ingredientId") Long ingredientId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get average cost per unit consumed for an ingredient
     */
    @Query("SELECT CASE WHEN SUM(bc.quantity) > 0 " +
           "THEN SUM(bc.totalCost) / SUM(bc.quantity) ELSE 0 END " +
           "FROM BatchConsumption bc WHERE bc.ingredient.id = :ingredientId")
    BigDecimal getAverageConsumptionCost(@Param("ingredientId") Long ingredientId);

    /**
     * Find consumptions by valuation method
     */
    List<BatchConsumption> findByValuationMethodOrderByConsumedAtDesc(ValuationMethod method);

    /**
     * Count consumptions for an order
     */
    long countByOrderId(Long orderId);

    /**
     * Delete consumptions for an order (for order cancellation)
     */
    void deleteByOrderId(Long orderId);

    /**
     * Find all consumptions for a restaurant in a date range
     */
    @Query("SELECT bc FROM BatchConsumption bc WHERE bc.ingredient.restaurant.id = :restaurantId " +
           "AND bc.consumedAt >= :startDate AND bc.consumedAt <= :endDate " +
           "ORDER BY bc.consumedAt DESC")
    List<BatchConsumption> findByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
