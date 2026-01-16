package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    /**
     * Find all batches for an ingredient, ordered by expiry date (FEFO)
     */
    @Query("SELECT b FROM InventoryBatch b WHERE b.ingredient.id = :ingredientId " +
           "ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, b.expiryDate ASC")
    List<InventoryBatch> findByIngredientIdOrderByExpiryDateAsc(@Param("ingredientId") Long ingredientId);

    /**
     * Find active batches for an ingredient, ordered by expiry date (FEFO)
     */
    @Query("SELECT b FROM InventoryBatch b WHERE b.ingredient.id = :ingredientId " +
           "AND b.status = 'ACTIVE' AND b.quantity > 0 " +
           "ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, b.expiryDate ASC")
    List<InventoryBatch> findActiveBatchesFEFO(@Param("ingredientId") Long ingredientId);

    /**
     * Find batches expiring within the specified days for a restaurant
     */
    @Query("SELECT b FROM InventoryBatch b WHERE b.ingredient.restaurant.id = :restaurantId " +
           "AND b.status = 'ACTIVE' AND b.quantity > 0 " +
           "AND b.expiryDate IS NOT NULL AND b.expiryDate <= :expiryThreshold " +
           "ORDER BY b.expiryDate ASC")
    List<InventoryBatch> findExpiringBatches(
            @Param("restaurantId") Long restaurantId,
            @Param("expiryThreshold") LocalDate expiryThreshold);

    /**
     * Find expired batches (past expiry date but still active)
     */
    @Query("SELECT b FROM InventoryBatch b WHERE b.ingredient.restaurant.id = :restaurantId " +
           "AND b.status = 'ACTIVE' AND b.quantity > 0 " +
           "AND b.expiryDate IS NOT NULL AND b.expiryDate < :today " +
           "ORDER BY b.expiryDate ASC")
    List<InventoryBatch> findExpiredBatches(
            @Param("restaurantId") Long restaurantId,
            @Param("today") LocalDate today);

    /**
     * Find batch by ingredient and batch number
     */
    Optional<InventoryBatch> findByIngredientIdAndBatchNumber(Long ingredientId, String batchNumber);

    /**
     * Find all batches for a restaurant
     */
    @Query("SELECT b FROM InventoryBatch b WHERE b.ingredient.restaurant.id = :restaurantId " +
           "ORDER BY b.ingredient.name, b.expiryDate ASC")
    List<InventoryBatch> findByRestaurant_Id(@Param("restaurantId") Long restaurantId);

    /**
     * Count active batches for an ingredient
     */
    @Query("SELECT COUNT(b) FROM InventoryBatch b WHERE b.ingredient.id = :ingredientId " +
           "AND b.status = 'ACTIVE' AND b.quantity > 0")
    long countActiveBatches(@Param("ingredientId") Long ingredientId);

    /**
     * Get total quantity across all active batches for an ingredient
     */
    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM InventoryBatch b " +
           "WHERE b.ingredient.id = :ingredientId AND b.status = 'ACTIVE'")
    BigDecimal getTotalActiveQuantity(@Param("ingredientId") Long ingredientId);

    /**
     * Get total quantity across active, non-expired batches for an ingredient
     */
    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM InventoryBatch b " +
           "WHERE b.ingredient.id = :ingredientId AND b.status = 'ACTIVE' " +
           "AND (b.expiryDate IS NULL OR b.expiryDate >= :today)")
    BigDecimal getEffectiveQuantity(
            @Param("ingredientId") Long ingredientId,
            @Param("today") LocalDate today);

    /**
     * Find batches by PO reference
     */
    List<InventoryBatch> findByPoReference(String poReference);

    /**
     * Count expiring batches for a restaurant within given days
     */
    @Query("SELECT COUNT(b) FROM InventoryBatch b WHERE b.ingredient.restaurant.id = :restaurantId " +
           "AND b.status = 'ACTIVE' AND b.quantity > 0 " +
           "AND b.expiryDate IS NOT NULL AND b.expiryDate <= :expiryThreshold AND b.expiryDate >= :today")
    long countExpiringBatches(
            @Param("restaurantId") Long restaurantId,
            @Param("today") LocalDate today,
            @Param("expiryThreshold") LocalDate expiryThreshold);

    /**
     * Count expired batches for a restaurant
     */
    @Query("SELECT COUNT(b) FROM InventoryBatch b WHERE b.ingredient.restaurant.id = :restaurantId " +
           "AND b.status = 'ACTIVE' AND b.quantity > 0 " +
           "AND b.expiryDate IS NOT NULL AND b.expiryDate < :today")
    long countExpiredBatches(@Param("restaurantId") Long restaurantId, @Param("today") LocalDate today);
}
