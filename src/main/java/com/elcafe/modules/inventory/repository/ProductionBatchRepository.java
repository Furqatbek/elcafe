package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.ProductionBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long> {

    /**
     * Find batches for a restaurant filtered by status
     */
    List<ProductionBatch> findByRestaurantIdAndStatus(Long restaurantId, ProductionBatch.Status status);

    /**
     * Find batches for a restaurant, ordered by creation date descending
     */
    List<ProductionBatch> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    /**
     * Find batches for a product with matching statuses (e.g. READY, SERVING)
     */
    List<ProductionBatch> findByProductIdAndStatusIn(Long productId, List<ProductionBatch.Status> statuses);

    /**
     * Find available batches for a product — READY/SERVING with remaining > 0, ordered FEFO
     */
    @Query("SELECT pb FROM ProductionBatch pb WHERE pb.product.id = :productId " +
           "AND pb.status IN ('READY', 'SERVING') AND pb.remainingQuantity > 0 " +
           "ORDER BY CASE WHEN pb.expiresAt IS NULL THEN 1 ELSE 0 END, pb.expiresAt ASC")
    List<ProductionBatch> findAvailableByProduct(@Param("productId") Long productId);

    /**
     * Find active batches (READY/SERVING) for a restaurant with remaining quantity
     */
    @Query("SELECT pb FROM ProductionBatch pb WHERE pb.restaurant.id = :restaurantId " +
           "AND pb.status IN ('READY', 'SERVING') AND pb.remainingQuantity > 0 " +
           "ORDER BY pb.name, CASE WHEN pb.expiresAt IS NULL THEN 1 ELSE 0 END, pb.expiresAt ASC")
    List<ProductionBatch> findActiveBatches(@Param("restaurantId") Long restaurantId);

    /**
     * Total production cost for a restaurant in a date range
     */
    @Query("SELECT COALESCE(SUM(pb.totalInputCost), 0) FROM ProductionBatch pb " +
           "WHERE pb.restaurant.id = :restaurantId " +
           "AND pb.completedAt >= :startDate AND pb.completedAt <= :endDate")
    BigDecimal getTotalProductionCost(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Average cost per unit for a product across completed batches
     */
    @Query("SELECT CASE WHEN SUM(pb.outputQuantity) > 0 " +
           "THEN SUM(pb.totalInputCost) / SUM(pb.outputQuantity) ELSE 0 END " +
           "FROM ProductionBatch pb WHERE pb.product.id = :productId " +
           "AND pb.status NOT IN ('DRAFT', 'WASTED')")
    BigDecimal getAverageCostPerUnit(@Param("productId") Long productId);

    /**
     * Production cost breakdown by product for a restaurant in a date range
     */
    @Query("SELECT pb.product.id, pb.name, COUNT(pb), SUM(pb.totalInputCost), " +
           "CASE WHEN SUM(pb.outputQuantity) > 0 " +
           "THEN SUM(pb.totalInputCost) / SUM(pb.outputQuantity) ELSE 0 END " +
           "FROM ProductionBatch pb WHERE pb.restaurant.id = :restaurantId " +
           "AND pb.completedAt >= :startDate AND pb.completedAt <= :endDate " +
           "GROUP BY pb.product.id, pb.name " +
           "ORDER BY SUM(pb.totalInputCost) DESC")
    List<Object[]> getProductionCostReport(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Check if a batch number already exists
     */
    boolean existsByBatchNumber(String batchNumber);
}
