package com.elcafe.modules.push.repository;

import com.elcafe.modules.push.entity.PushNotificationLog;
import com.elcafe.modules.push.enums.PushNotificationStatus;
import com.elcafe.modules.push.enums.PushNotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PushNotificationLogRepository extends JpaRepository<PushNotificationLog, Long> {

    /**
     * Find logs by customer
     */
    Page<PushNotificationLog> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    /**
     * Find logs by subscription
     */
    List<PushNotificationLog> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    /**
     * Find logs by campaign
     */
    Page<PushNotificationLog> findByCampaignIdOrderByCreatedAtDesc(Long campaignId, Pageable pageable);

    /**
     * Find logs by status
     */
    Page<PushNotificationLog> findByStatusOrderByCreatedAtDesc(PushNotificationStatus status, Pageable pageable);

    /**
     * Find logs by notification type
     */
    Page<PushNotificationLog> findByNotificationTypeOrderByCreatedAtDesc(PushNotificationType type, Pageable pageable);

    /**
     * Count by status
     */
    long countByStatus(PushNotificationStatus status);

    /**
     * Count by campaign and status
     */
    long countByCampaignIdAndStatus(Long campaignId, PushNotificationStatus status);

    /**
     * Count sent in date range
     */
    @Query("SELECT COUNT(p) FROM PushNotificationLog p WHERE p.sentAt BETWEEN :start AND :end")
    long countSentBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Count clicked in date range
     */
    @Query("SELECT COUNT(p) FROM PushNotificationLog p WHERE p.clickedAt BETWEEN :start AND :end")
    long countClickedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Get click rate for campaign
     */
    @Query("SELECT CAST(SUM(CASE WHEN p.clickedAt IS NOT NULL THEN 1 ELSE 0 END) AS double) / COUNT(p) " +
           "FROM PushNotificationLog p WHERE p.campaignId = :campaignId AND p.status != 'FAILED'")
    Double getClickRateByCampaignId(@Param("campaignId") Long campaignId);

    /**
     * Find pending logs older than given time (for retry or cleanup)
     */
    @Query("SELECT p FROM PushNotificationLog p WHERE p.status = 'PENDING' AND p.createdAt < :cutoff")
    List<PushNotificationLog> findStalePendingLogs(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Delete old logs for cleanup
     */
    @Query("DELETE FROM PushNotificationLog p WHERE p.createdAt < :cutoffDate")
    int deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
}
