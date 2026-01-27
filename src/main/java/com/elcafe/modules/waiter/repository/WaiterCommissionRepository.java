package com.elcafe.modules.waiter.repository;

import com.elcafe.modules.waiter.entity.WaiterCommission;
import com.elcafe.modules.waiter.enums.CommissionStatus;
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
public interface WaiterCommissionRepository extends JpaRepository<WaiterCommission, Long> {

    /**
     * Find commission by waiter and order
     */
    Optional<WaiterCommission> findByWaiterIdAndOrderId(Long waiterId, Long orderId);

    /**
     * Check if commission already exists for waiter and order
     */
    boolean existsByWaiterIdAndOrderId(Long waiterId, Long orderId);

    /**
     * Find all commissions for a waiter
     */
    Page<WaiterCommission> findByWaiterId(Long waiterId, Pageable pageable);

    /**
     * Find all commissions for a waiter within a date range
     */
    @Query("SELECT wc FROM WaiterCommission wc WHERE wc.waiter.id = :waiterId " +
           "AND wc.createdAt >= :startDate AND wc.createdAt <= :endDate ORDER BY wc.createdAt DESC")
    List<WaiterCommission> findByWaiterIdAndDateRange(
            @Param("waiterId") Long waiterId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find all commissions for a restaurant
     */
    Page<WaiterCommission> findByRestaurantId(Long restaurantId, Pageable pageable);

    /**
     * Find commissions by status
     */
    Page<WaiterCommission> findByStatus(CommissionStatus status, Pageable pageable);

    /**
     * Find pending commissions for a waiter
     */
    List<WaiterCommission> findByWaiterIdAndStatus(Long waiterId, CommissionStatus status);

    /**
     * Find pending commissions for a restaurant within date range
     */
    @Query("SELECT wc FROM WaiterCommission wc WHERE wc.restaurant.id = :restaurantId " +
           "AND wc.status = :status AND wc.createdAt >= :startDate AND wc.createdAt <= :endDate")
    List<WaiterCommission> findByRestaurantIdAndStatusAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("status") CommissionStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Calculate total commission for a waiter within date range
     */
    @Query("SELECT COALESCE(SUM(wc.commissionAmount), 0) FROM WaiterCommission wc " +
           "WHERE wc.waiter.id = :waiterId AND wc.createdAt >= :startDate AND wc.createdAt <= :endDate")
    BigDecimal getTotalCommissionByWaiterAndDateRange(
            @Param("waiterId") Long waiterId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Calculate total pending commission for a waiter
     */
    @Query("SELECT COALESCE(SUM(wc.commissionAmount), 0) FROM WaiterCommission wc " +
           "WHERE wc.waiter.id = :waiterId AND wc.status = 'PENDING'")
    BigDecimal getTotalPendingCommissionByWaiter(@Param("waiterId") Long waiterId);

    /**
     * Calculate total paid commission for a waiter
     */
    @Query("SELECT COALESCE(SUM(wc.commissionAmount), 0) FROM WaiterCommission wc " +
           "WHERE wc.waiter.id = :waiterId AND wc.status = 'PAID'")
    BigDecimal getTotalPaidCommissionByWaiter(@Param("waiterId") Long waiterId);

    /**
     * Get commission summary for a waiter
     */
    @Query("SELECT new map(" +
           "COUNT(wc) as totalCommissions, " +
           "COALESCE(SUM(wc.orderTotal), 0) as totalOrderValue, " +
           "COALESCE(SUM(wc.commissionAmount), 0) as totalCommissionEarned, " +
           "COALESCE(SUM(CASE WHEN wc.status = 'PENDING' THEN wc.commissionAmount ELSE 0 END), 0) as pendingCommission, " +
           "COALESCE(SUM(CASE WHEN wc.status = 'PAID' THEN wc.commissionAmount ELSE 0 END), 0) as paidCommission) " +
           "FROM WaiterCommission wc WHERE wc.waiter.id = :waiterId")
    Object getCommissionSummaryByWaiter(@Param("waiterId") Long waiterId);

    /**
     * Get commission summary for a waiter within date range
     */
    @Query("SELECT new map(" +
           "COUNT(wc) as totalCommissions, " +
           "COALESCE(SUM(wc.orderTotal), 0) as totalOrderValue, " +
           "COALESCE(SUM(wc.commissionAmount), 0) as totalCommissionEarned, " +
           "COALESCE(SUM(CASE WHEN wc.status = 'PENDING' THEN wc.commissionAmount ELSE 0 END), 0) as pendingCommission, " +
           "COALESCE(SUM(CASE WHEN wc.status = 'PAID' THEN wc.commissionAmount ELSE 0 END), 0) as paidCommission) " +
           "FROM WaiterCommission wc WHERE wc.waiter.id = :waiterId " +
           "AND wc.createdAt >= :startDate AND wc.createdAt <= :endDate")
    Object getCommissionSummaryByWaiterAndDateRange(
            @Param("waiterId") Long waiterId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get all pending commissions for processing payroll
     */
    @Query("SELECT wc FROM WaiterCommission wc WHERE wc.waiter.id = :waiterId " +
           "AND wc.status = 'PENDING' AND wc.createdAt >= :startDate AND wc.createdAt <= :endDate")
    List<WaiterCommission> findPendingCommissionsForPayroll(
            @Param("waiterId") Long waiterId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count commissions by status for a waiter
     */
    @Query("SELECT wc.status, COUNT(wc) FROM WaiterCommission wc WHERE wc.waiter.id = :waiterId GROUP BY wc.status")
    List<Object[]> countCommissionsByStatusForWaiter(@Param("waiterId") Long waiterId);

    /**
     * Get daily commission totals for a waiter
     */
    @Query("SELECT CAST(wc.createdAt AS DATE) as date, SUM(wc.commissionAmount) as total " +
           "FROM WaiterCommission wc WHERE wc.waiter.id = :waiterId " +
           "AND wc.createdAt >= :startDate AND wc.createdAt <= :endDate " +
           "GROUP BY CAST(wc.createdAt AS DATE) ORDER BY CAST(wc.createdAt AS DATE)")
    List<Object[]> getDailyCommissionTotals(
            @Param("waiterId") Long waiterId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
