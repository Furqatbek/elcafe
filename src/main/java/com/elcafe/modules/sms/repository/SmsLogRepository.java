package com.elcafe.modules.sms.repository;

import com.elcafe.modules.sms.entity.SmsLog;
import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.sms.enums.SmsMessageType;
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
public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {

    Page<SmsLog> findByCustomerId(Long customerId, Pageable pageable);

    Page<SmsLog> findByPhone(String phone, Pageable pageable);

    Page<SmsLog> findByStatus(MessageStatus status, Pageable pageable);

    Page<SmsLog> findByMessageType(SmsMessageType messageType, Pageable pageable);

    Page<SmsLog> findByCampaignId(Long campaignId, Pageable pageable);

    Optional<SmsLog> findByEskizMessageId(Long eskizMessageId);

    @Query("SELECT l FROM SmsLog l WHERE l.createdAt >= :since ORDER BY l.createdAt DESC")
    Page<SmsLog> findRecentLogs(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT COUNT(l) FROM SmsLog l WHERE l.status = :status AND l.createdAt >= :since")
    long countByStatusSince(@Param("status") MessageStatus status, @Param("since") LocalDateTime since);

    @Query("SELECT SUM(l.cost) FROM SmsLog l WHERE l.createdAt >= :since")
    BigDecimal getTotalCostSince(@Param("since") LocalDateTime since);

    @Query("SELECT l.status, COUNT(l) FROM SmsLog l WHERE l.createdAt >= :since GROUP BY l.status")
    List<Object[]> getStatusCountsSince(@Param("since") LocalDateTime since);

    @Query("SELECT l.messageType, COUNT(l) FROM SmsLog l WHERE l.createdAt >= :since GROUP BY l.messageType")
    List<Object[]> getTypeCountsSince(@Param("since") LocalDateTime since);

    @Query("SELECT DATE(l.createdAt), COUNT(l), SUM(CASE WHEN l.status = 'DELIVERED' THEN 1 ELSE 0 END) " +
           "FROM SmsLog l WHERE l.createdAt >= :since GROUP BY DATE(l.createdAt) ORDER BY DATE(l.createdAt)")
    List<Object[]> getDailyStatsSince(@Param("since") LocalDateTime since);

    @Query("SELECT l FROM SmsLog l WHERE l.status IN ('SENT', 'WAITING') AND l.sentAt IS NOT NULL AND l.sentAt < :before")
    List<SmsLog> findSentLogsForStatusUpdate(@Param("before") LocalDateTime before);

    @Query("SELECT COUNT(l) FROM SmsLog l WHERE l.customerId = :customerId AND l.createdAt >= :since")
    long countByCustomerSince(@Param("customerId") Long customerId, @Param("since") LocalDateTime since);

    // Prevent sending duplicate messages
    boolean existsByCustomerIdAndMessageAndCreatedAtAfter(Long customerId, String message, LocalDateTime after);

    @Query("SELECT l FROM SmsLog l WHERE " +
           "(LOWER(l.phone) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.message) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY l.createdAt DESC")
    Page<SmsLog> searchLogs(@Param("search") String search, Pageable pageable);

    // Additional methods for SmsLogService
    Page<SmsLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<SmsLog> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<SmsLog> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);

    Page<SmsLog> findByMessageTypeOrderByCreatedAtDesc(SmsMessageType messageType, Pageable pageable);

    Page<SmsLog> findByStatusOrderByCreatedAtDesc(MessageStatus status, Pageable pageable);

    List<SmsLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    boolean existsByCustomerIdAndAutomationRuleIdAndCreatedAtAfter(Long customerId, Long automationRuleId, LocalDateTime after);

    List<SmsLog> findByCreatedAtBefore(LocalDateTime before);
}
