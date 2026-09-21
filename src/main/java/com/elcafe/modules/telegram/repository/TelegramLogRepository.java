package com.elcafe.modules.telegram.repository;

import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.telegram.entity.TelegramLog;
import com.elcafe.modules.telegram.enums.TelegramMessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramLogRepository extends JpaRepository<TelegramLog, Long> {

    Page<TelegramLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<TelegramLog> findBySubscriberIdOrderByCreatedAtDesc(Long subscriberId);

    List<TelegramLog> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);

    Page<TelegramLog> findByMessageTypeOrderByCreatedAtDesc(TelegramMessageType messageType, Pageable pageable);

    Page<TelegramLog> findByStatusOrderByCreatedAtDesc(MessageStatus status, Pageable pageable);

    List<TelegramLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    Optional<TelegramLog> findByTelegramMessageId(Long telegramMessageId);

    boolean existsBySubscriberIdAndAutomationRuleIdAndCreatedAtAfter(Long subscriberId, Long automationRuleId, LocalDateTime after);

    @Query("SELECT COUNT(l) FROM TelegramLog l WHERE l.status = :status AND l.createdAt >= :since")
    long countByStatusSince(@Param("status") MessageStatus status, @Param("since") LocalDateTime since);

    @Query("SELECT l.status, COUNT(l) FROM TelegramLog l WHERE l.createdAt >= :since GROUP BY l.status")
    List<Object[]> getStatusCountsSince(@Param("since") LocalDateTime since);

    @Query("SELECT l.messageType, COUNT(l) FROM TelegramLog l WHERE l.createdAt >= :since GROUP BY l.messageType")
    List<Object[]> getTypeCountsSince(@Param("since") LocalDateTime since);

    List<TelegramLog> findByCreatedAtBefore(LocalDateTime before);
}
