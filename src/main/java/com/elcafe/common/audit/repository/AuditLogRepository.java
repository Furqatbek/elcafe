package com.elcafe.common.audit.repository;

import com.elcafe.common.audit.entity.AuditAction;
import com.elcafe.common.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find audit logs by restaurant within a time range
     */
    Page<AuditLog> findByRestaurantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long restaurantId,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            Pageable pageable);

    /**
     * Find audit logs for a specific order
     */
    List<AuditLog> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    /**
     * Find audit logs for a specific user
     */
    Page<AuditLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            Pageable pageable);

    /**
     * Find audit logs by action type
     */
    Page<AuditLog> findByRestaurantIdAndActionOrderByCreatedAtDesc(
            Long restaurantId,
            AuditAction action,
            Pageable pageable);

    /**
     * Find failed audit logs (potential security issues)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.restaurantId = :restaurantId " +
           "AND a.result IN ('FAILURE', 'DENIED') " +
           "AND a.createdAt > :since ORDER BY a.createdAt DESC")
    List<AuditLog> findSecurityIncidents(
            @Param("restaurantId") Long restaurantId,
            @Param("since") OffsetDateTime since);

    /**
     * Find refund/void operations for compliance review
     */
    @Query("SELECT a FROM AuditLog a WHERE a.restaurantId = :restaurantId " +
           "AND a.action IN ('REFUND_COMPLETED', 'VOID_COMPLETED', 'ORDER_VOIDED') " +
           "AND a.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY a.createdAt DESC")
    List<AuditLog> findFinancialReversals(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Find operations by IP address (fraud detection)
     */
    List<AuditLog> findByIpAddressAndCreatedAtAfterOrderByCreatedAtDesc(
            String ipAddress,
            OffsetDateTime since);

    /**
     * Count actions by user in time window (rate limiting/anomaly detection)
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.action = :action AND a.createdAt > :since")
    long countUserActionsSince(
            @Param("userId") Long userId,
            @Param("action") AuditAction action,
            @Param("since") OffsetDateTime since);

    /**
     * Find pending approval requests
     */
    @Query("SELECT a FROM AuditLog a WHERE a.restaurantId = :restaurantId " +
           "AND a.result = 'PENDING_APPROVAL' ORDER BY a.createdAt ASC")
    List<AuditLog> findPendingApprovals(@Param("restaurantId") Long restaurantId);
}
