package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.ProductionBatchInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductionBatchInputRepository extends JpaRepository<ProductionBatchInput, Long> {

    /**
     * Find all inputs for a production batch
     */
    List<ProductionBatchInput> findByProductionBatchId(Long productionBatchId);

    /**
     * Find inputs for a batch ordered by cost descending (most expensive first)
     */
    List<ProductionBatchInput> findByProductionBatchIdOrderByTotalCostDesc(Long productionBatchId);

    /**
     * Ingredient usage report: total quantity and cost per ingredient across batches in a date range
     */
    @Query("SELECT pbi.ingredient.id, pbi.ingredient.name, pbi.unit, " +
           "SUM(pbi.actualQuantity), SUM(pbi.totalCost) " +
           "FROM ProductionBatchInput pbi " +
           "WHERE pbi.productionBatch.restaurant.id = :restaurantId " +
           "AND pbi.createdAt >= :startDate AND pbi.createdAt <= :endDate " +
           "GROUP BY pbi.ingredient.id, pbi.ingredient.name, pbi.unit " +
           "ORDER BY SUM(pbi.totalCost) DESC")
    List<Object[]> getIngredientUsageReport(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Total cost of inputs for a specific batch
     */
    @Query("SELECT COALESCE(SUM(pbi.totalCost), 0) FROM ProductionBatchInput pbi " +
           "WHERE pbi.productionBatch.id = :batchId")
    BigDecimal getTotalInputCost(@Param("batchId") Long batchId);
}
