package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.StockCountItem;
import com.elcafe.modules.inventory.entity.StockVarianceHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockVarianceHistoryRepository extends JpaRepository<StockVarianceHistory, Long> {

    List<StockVarianceHistory> findByRestaurantId(Long restaurantId);

    Page<StockVarianceHistory> findByRestaurantId(Long restaurantId, Pageable pageable);

    List<StockVarianceHistory> findByIngredientId(Long ingredientId);

    List<StockVarianceHistory> findByStockCountId(Long stockCountId);

    @Query("SELECT svh FROM StockVarianceHistory svh WHERE svh.restaurant.id = :restaurantId " +
           "AND svh.varianceDate BETWEEN :startDate AND :endDate " +
           "ORDER BY svh.varianceDate DESC")
    List<StockVarianceHistory> findByRestaurantIdAndDateRange(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT svh FROM StockVarianceHistory svh WHERE svh.ingredient.id = :ingredientId " +
           "AND svh.varianceDate BETWEEN :startDate AND :endDate " +
           "ORDER BY svh.varianceDate DESC")
    List<StockVarianceHistory> findByIngredientIdAndDateRange(
            Long ingredientId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT svh FROM StockVarianceHistory svh WHERE svh.restaurant.id = :restaurantId " +
           "AND svh.varianceReason = :reason " +
           "ORDER BY svh.varianceDate DESC")
    List<StockVarianceHistory> findByRestaurantIdAndVarianceReason(
            Long restaurantId, StockCountItem.VarianceReason reason);

    @Query("SELECT COALESCE(SUM(svh.varianceValue), 0) FROM StockVarianceHistory svh " +
           "WHERE svh.restaurant.id = :restaurantId " +
           "AND svh.varianceDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalVarianceValueByDateRange(Long restaurantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT svh.varianceReason, COUNT(svh), SUM(svh.varianceValue) " +
           "FROM StockVarianceHistory svh " +
           "WHERE svh.restaurant.id = :restaurantId " +
           "AND svh.varianceDate BETWEEN :startDate AND :endDate " +
           "GROUP BY svh.varianceReason")
    List<Object[]> getVarianceBreakdownByReason(Long restaurantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT svh.ingredient.id, svh.ingredient.name, COUNT(svh), SUM(svh.varianceValue) " +
           "FROM StockVarianceHistory svh " +
           "WHERE svh.restaurant.id = :restaurantId " +
           "AND svh.varianceDate BETWEEN :startDate AND :endDate " +
           "GROUP BY svh.ingredient.id, svh.ingredient.name " +
           "ORDER BY SUM(ABS(svh.varianceValue)) DESC")
    List<Object[]> getTopVarianceIngredients(Long restaurantId, LocalDate startDate, LocalDate endDate, Pageable pageable);
}
