package com.elcafe.modules.waiter.repository;

import com.elcafe.modules.waiter.entity.WaiterPerformance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WaiterPerformanceRepository extends JpaRepository<WaiterPerformance, Long> {

    /**
     * Find performance for a specific waiter on a specific date
     */
    Optional<WaiterPerformance> findByWaiterIdAndPerformanceDate(Long waiterId, LocalDate date);

    /**
     * Find all performance records for a waiter within a date range
     */
    List<WaiterPerformance> findByWaiterIdAndPerformanceDateBetweenOrderByPerformanceDateDesc(
            Long waiterId, LocalDate startDate, LocalDate endDate);

    /**
     * Find all performance records for a restaurant on a specific date
     */
    List<WaiterPerformance> findByRestaurantIdAndPerformanceDate(Long restaurantId, LocalDate date);

    /**
     * Find all performance records for a restaurant within a date range
     */
    List<WaiterPerformance> findByRestaurantIdAndPerformanceDateBetweenOrderByPerformanceDateDesc(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    /**
     * Get paginated performance history for a waiter
     */
    Page<WaiterPerformance> findByWaiterIdOrderByPerformanceDateDesc(Long waiterId, Pageable pageable);

    /**
     * Get aggregated metrics for a waiter within a date range
     */
    @Query("SELECT " +
           "SUM(p.totalOrders) as totalOrders, " +
           "SUM(p.totalRevenue) as totalRevenue, " +
           "SUM(p.totalTips) as totalTips, " +
           "AVG(p.avgTicketValue) as avgTicket, " +
           "AVG(p.avgServiceTimeMinutes) as avgServiceTime, " +
           "SUM(p.complaintsCount) as complaints, " +
           "SUM(p.complimentsCount) as compliments, " +
           "AVG(p.kpiScore) as avgKpiScore, " +
           "SUM(p.bonusEarned) as totalBonus " +
           "FROM WaiterPerformance p " +
           "WHERE p.waiter.id = :waiterId AND p.performanceDate BETWEEN :startDate AND :endDate")
    Object[] getAggregatedMetrics(@Param("waiterId") Long waiterId,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate);

    /**
     * Get top performers for a restaurant within a date range
     */
    @Query("SELECT p.waiter.id, p.waiter.name, " +
           "SUM(p.totalRevenue) as revenue, " +
           "SUM(p.totalOrders) as orders, " +
           "AVG(p.kpiScore) as avgKpi " +
           "FROM WaiterPerformance p " +
           "WHERE p.restaurant.id = :restaurantId AND p.performanceDate BETWEEN :startDate AND :endDate " +
           "GROUP BY p.waiter.id, p.waiter.name " +
           "ORDER BY AVG(p.kpiScore) DESC")
    List<Object[]> getTopPerformers(@Param("restaurantId") Long restaurantId,
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);

    /**
     * Get performance summary by waiter for leaderboard
     */
    @Query("SELECT p.waiter.id, p.waiter.name, " +
           "SUM(p.totalRevenue) as totalRevenue, " +
           "SUM(p.totalOrders) as totalOrders, " +
           "AVG(p.avgCustomerRating) as avgRating, " +
           "AVG(p.kpiScore) as avgKpiScore " +
           "FROM WaiterPerformance p " +
           "WHERE p.restaurant.id = :restaurantId AND p.performanceDate BETWEEN :startDate AND :endDate " +
           "GROUP BY p.waiter.id, p.waiter.name")
    List<Object[]> getLeaderboard(@Param("restaurantId") Long restaurantId,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate);

    /**
     * Count working days for a waiter
     */
    @Query("SELECT COUNT(DISTINCT p.performanceDate) FROM WaiterPerformance p " +
           "WHERE p.waiter.id = :waiterId AND p.performanceDate BETWEEN :startDate AND :endDate")
    Long countWorkingDays(@Param("waiterId") Long waiterId,
                          @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate);

    /**
     * Get total bonus earned
     */
    @Query("SELECT COALESCE(SUM(p.bonusEarned), 0) FROM WaiterPerformance p " +
           "WHERE p.waiter.id = :waiterId AND p.performanceDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalBonusEarned(@Param("waiterId") Long waiterId,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);
}
