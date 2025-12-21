package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.WasteRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface WasteRecordRepository extends JpaRepository<WasteRecord, Long> {

    List<WasteRecord> findByRestaurantId(Long restaurantId);

    Page<WasteRecord> findByRestaurantId(Long restaurantId, Pageable pageable);

    List<WasteRecord> findByIngredientId(Long ingredientId);

    List<WasteRecord> findByBatchId(Long batchId);

    @Query("SELECT w FROM WasteRecord w WHERE w.restaurant.id = :restaurantId " +
           "AND w.wasteDate BETWEEN :startDate AND :endDate " +
           "ORDER BY w.wasteDate DESC")
    List<WasteRecord> findByRestaurantIdAndDateRange(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT w FROM WasteRecord w WHERE w.restaurant.id = :restaurantId " +
           "AND w.wasteReason = :reason " +
           "ORDER BY w.wasteDate DESC")
    List<WasteRecord> findByRestaurantIdAndReason(
            Long restaurantId, WasteRecord.WasteReason reason);

    @Query("SELECT w FROM WasteRecord w WHERE w.restaurant.id = :restaurantId " +
           "AND w.wasteDate BETWEEN :startDate AND :endDate " +
           "AND w.wasteReason = :reason " +
           "ORDER BY w.wasteDate DESC")
    List<WasteRecord> findByRestaurantIdAndDateRangeAndReason(
            Long restaurantId, LocalDate startDate, LocalDate endDate, WasteRecord.WasteReason reason);

    @Query("SELECT w FROM WasteRecord w WHERE w.restaurant.id = :restaurantId " +
           "AND w.ingredient.id = :ingredientId " +
           "AND w.wasteDate BETWEEN :startDate AND :endDate " +
           "ORDER BY w.wasteDate DESC")
    List<WasteRecord> findByRestaurantIdAndIngredientIdAndDateRange(
            Long restaurantId, Long ingredientId, LocalDate startDate, LocalDate endDate);

    // Aggregate queries for reporting
    @Query("SELECT COALESCE(SUM(w.totalCost), 0) FROM WasteRecord w " +
           "WHERE w.restaurant.id = :restaurantId " +
           "AND w.wasteDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalWasteCost(Long restaurantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COALESCE(SUM(w.quantity), 0) FROM WasteRecord w " +
           "WHERE w.restaurant.id = :restaurantId " +
           "AND w.wasteDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalWasteQuantity(Long restaurantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COUNT(w) FROM WasteRecord w " +
           "WHERE w.restaurant.id = :restaurantId " +
           "AND w.wasteDate BETWEEN :startDate AND :endDate")
    Long getWasteRecordCount(Long restaurantId, LocalDate startDate, LocalDate endDate);

    // Waste by reason breakdown
    @Query("SELECT w.wasteReason, COUNT(w), COALESCE(SUM(w.quantity), 0), COALESCE(SUM(w.totalCost), 0) " +
           "FROM WasteRecord w " +
           "WHERE w.restaurant.id = :restaurantId " +
           "AND w.wasteDate BETWEEN :startDate AND :endDate " +
           "GROUP BY w.wasteReason " +
           "ORDER BY SUM(w.totalCost) DESC")
    List<Object[]> getWasteBreakdownByReason(Long restaurantId, LocalDate startDate, LocalDate endDate);

    // Top wasted ingredients
    @Query("SELECT w.ingredient.id, w.ingredient.name, COUNT(w), COALESCE(SUM(w.quantity), 0), COALESCE(SUM(w.totalCost), 0) " +
           "FROM WasteRecord w " +
           "WHERE w.restaurant.id = :restaurantId " +
           "AND w.wasteDate BETWEEN :startDate AND :endDate " +
           "GROUP BY w.ingredient.id, w.ingredient.name " +
           "ORDER BY SUM(w.totalCost) DESC")
    List<Object[]> getTopWastedIngredients(Long restaurantId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    // Daily waste totals for trend chart
    @Query("SELECT w.wasteDate, COUNT(w), COALESCE(SUM(w.totalCost), 0) " +
           "FROM WasteRecord w " +
           "WHERE w.restaurant.id = :restaurantId " +
           "AND w.wasteDate BETWEEN :startDate AND :endDate " +
           "GROUP BY w.wasteDate " +
           "ORDER BY w.wasteDate ASC")
    List<Object[]> getDailyWasteTotals(Long restaurantId, LocalDate startDate, LocalDate endDate);

    // Most common waste reason
    @Query("SELECT w.wasteReason FROM WasteRecord w " +
           "WHERE w.restaurant.id = :restaurantId " +
           "AND w.wasteDate BETWEEN :startDate AND :endDate " +
           "GROUP BY w.wasteReason " +
           "ORDER BY COUNT(w) DESC " +
           "LIMIT 1")
    WasteRecord.WasteReason getMostCommonWasteReason(Long restaurantId, LocalDate startDate, LocalDate endDate);
}
