package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.ProductionBatchConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductionBatchConsumptionRepository extends JpaRepository<ProductionBatchConsumption, Long> {

    /**
     * Find all consumptions for an order (for COGS calculation)
     */
    List<ProductionBatchConsumption> findByOrderId(Long orderId);

    /**
     * Find all consumptions from a production batch (usage report)
     */
    List<ProductionBatchConsumption> findByProductionBatchIdOrderByConsumedAtDesc(Long productionBatchId);

    /**
     * Find consumptions for a production batch
     */
    List<ProductionBatchConsumption> findByProductionBatchId(Long productionBatchId);

    /**
     * Total COGS from production batches for an order
     */
    @Query("SELECT COALESCE(SUM(pbc.totalCost), 0) FROM ProductionBatchConsumption pbc " +
           "WHERE pbc.orderId = :orderId")
    BigDecimal calculateOrderCOGS(@Param("orderId") Long orderId);

    /**
     * Total consumption cost for a restaurant in a date range (for analytics)
     */
    @Query("SELECT COALESCE(SUM(pbc.totalCost), 0) FROM ProductionBatchConsumption pbc " +
           "WHERE pbc.productionBatch.restaurant.id = :restaurantId " +
           "AND pbc.consumedAt >= :startDate AND pbc.consumedAt <= :endDate")
    BigDecimal getTotalConsumptionCost(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Consumption breakdown by batch for a restaurant in a date range
     */
    @Query("SELECT pbc.productionBatch.id, pbc.productionBatch.name, " +
           "SUM(pbc.quantity), SUM(pbc.totalCost) " +
           "FROM ProductionBatchConsumption pbc " +
           "WHERE pbc.productionBatch.restaurant.id = :restaurantId " +
           "AND pbc.consumedAt >= :startDate AND pbc.consumedAt <= :endDate " +
           "GROUP BY pbc.productionBatch.id, pbc.productionBatch.name " +
           "ORDER BY SUM(pbc.totalCost) DESC")
    List<Object[]> getConsumptionByBatch(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Delete consumptions for an order (for order cancellation/restoration)
     */
    void deleteByOrderId(Long orderId);

    /**
     * Count consumptions for an order
     */
    long countByOrderId(Long orderId);
}
