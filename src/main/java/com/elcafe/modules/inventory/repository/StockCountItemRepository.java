package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.StockCountItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface StockCountItemRepository extends JpaRepository<StockCountItem, Long> {

    List<StockCountItem> findByStockCountId(Long stockCountId);

    List<StockCountItem> findByStockCountIdAndStatus(Long stockCountId, StockCountItem.Status status);

    @Query("SELECT sci FROM StockCountItem sci WHERE sci.stockCount.id = :stockCountId " +
           "AND sci.varianceQuantity IS NOT NULL " +
           "AND sci.varianceQuantity <> 0 " +
           "ORDER BY ABS(sci.varianceQuantity) DESC")
    List<StockCountItem> findItemsWithVariance(Long stockCountId);

    @Query("SELECT sci FROM StockCountItem sci WHERE sci.stockCount.id = :stockCountId " +
           "AND sci.status = 'PENDING' " +
           "ORDER BY sci.ingredient.name ASC")
    List<StockCountItem> findPendingItems(Long stockCountId);

    @Query("SELECT COUNT(sci) FROM StockCountItem sci WHERE sci.stockCount.id = :stockCountId " +
           "AND sci.status = 'COUNTED'")
    int countCountedItems(Long stockCountId);

    @Query("SELECT COUNT(sci) FROM StockCountItem sci WHERE sci.stockCount.id = :stockCountId " +
           "AND sci.varianceQuantity IS NOT NULL " +
           "AND sci.varianceQuantity <> 0")
    int countItemsWithVariance(Long stockCountId);

    @Query("SELECT COALESCE(SUM(sci.varianceValue), 0) FROM StockCountItem sci " +
           "WHERE sci.stockCount.id = :stockCountId")
    BigDecimal getTotalVarianceValue(Long stockCountId);

    @Query("SELECT sci FROM StockCountItem sci WHERE sci.ingredient.id = :ingredientId " +
           "ORDER BY sci.stockCount.createdAt DESC")
    List<StockCountItem> findByIngredientId(Long ingredientId);
}
