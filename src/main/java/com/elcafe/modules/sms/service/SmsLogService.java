package com.elcafe.modules.sms.service;

import com.elcafe.modules.sms.entity.SmsLog;
import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.sms.enums.SmsMessageType;
import com.elcafe.modules.sms.repository.SmsLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsLogService {

    private final SmsLogRepository smsLogRepository;

    /**
     * Create a new SMS log entry
     */
    @Transactional
    public SmsLog createLog(SmsLog smsLog) {
        return smsLogRepository.save(smsLog);
    }

    /**
     * Update log status from Eskiz callback
     */
    @Transactional
    public void updateStatus(Long eskizMessageId, MessageStatus status, String errorMessage) {
        Optional<SmsLog> logOpt = smsLogRepository.findByEskizMessageId(eskizMessageId);
        if (logOpt.isPresent()) {
            SmsLog smsLog = logOpt.get();
            smsLog.setStatus(status);
            smsLog.setStatusUpdatedAt(LocalDateTime.now());

            if (status == MessageStatus.DELIVERED) {
                smsLog.setDeliveredAt(LocalDateTime.now());
            } else if (status == MessageStatus.FAILED || status == MessageStatus.REJECTED) {
                smsLog.setErrorMessage(errorMessage);
            }

            smsLogRepository.save(smsLog);
            log.debug("Updated SMS log status: messageId={}, status={}", eskizMessageId, status);
        }
    }

    /**
     * Get all logs with pagination
     */
    @Transactional(readOnly = true)
    public Page<SmsLog> getAllLogs(Pageable pageable) {
        return smsLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Get logs by customer ID
     */
    @Transactional(readOnly = true)
    public List<SmsLog> getLogsByCustomer(Long customerId) {
        return smsLogRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    /**
     * Get logs by campaign ID
     */
    @Transactional(readOnly = true)
    public List<SmsLog> getLogsByCampaign(Long campaignId) {
        return smsLogRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);
    }

    /**
     * Get logs by message type
     */
    @Transactional(readOnly = true)
    public Page<SmsLog> getLogsByType(SmsMessageType messageType, Pageable pageable) {
        return smsLogRepository.findByMessageTypeOrderByCreatedAtDesc(messageType, pageable);
    }

    /**
     * Get logs by status
     */
    @Transactional(readOnly = true)
    public Page<SmsLog> getLogsByStatus(MessageStatus status, Pageable pageable) {
        return smsLogRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    /**
     * Get logs within date range
     */
    @Transactional(readOnly = true)
    public List<SmsLog> getLogsByDateRange(LocalDateTime from, LocalDateTime to) {
        return smsLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }

    /**
     * Check if customer received specific automation message recently
     */
    @Transactional(readOnly = true)
    public boolean hasRecentAutomationMessage(Long customerId, Long automationRuleId, LocalDateTime since) {
        return smsLogRepository.existsByCustomerIdAndAutomationRuleIdAndCreatedAtAfter(
                customerId, automationRuleId, since);
    }

    /**
     * Get SMS statistics for dashboard
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(LocalDateTime from, LocalDateTime to) {
        // Aggregate in the DB (GROUP BY / SUM) instead of loading every log row in the window into memory
        // and counting in Java — a month of a shared account's SMS on a small heap was an OOM (PERF-4).
        Map<MessageStatus, Long> byStatus = toCountMap(smsLogRepository.getStatusCountsBetween(from, to));
        Map<SmsMessageType, Long> byType = toCountMap(smsLogRepository.getTypeCountsBetween(from, to));
        BigDecimal cost = smsLogRepository.getTotalCostBetween(from, to);

        long totalSent = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long delivered = count(byStatus, MessageStatus.DELIVERED);
        long failed = count(byStatus, MessageStatus.FAILED) + count(byStatus, MessageStatus.REJECTED);
        long pending = count(byStatus, MessageStatus.PENDING) + count(byStatus, MessageStatus.QUEUED)
                + count(byStatus, MessageStatus.SENT);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSent", totalSent);
        stats.put("delivered", delivered);
        stats.put("failed", failed);
        stats.put("pending", pending);
        stats.put("deliveryRate", totalSent > 0 ? (delivered * 100.0 / totalSent) : 0);
        stats.put("campaignMessages", count(byType, SmsMessageType.CAMPAIGN));
        stats.put("automationMessages", count(byType, SmsMessageType.AUTOMATION));
        stats.put("transactionalMessages", count(byType, SmsMessageType.TRANSACTIONAL));
        stats.put("manualMessages", count(byType, SmsMessageType.MANUAL));
        stats.put("totalCost", cost == null ? 0.0 : cost.doubleValue());
        return stats;
    }

    private static <K> Map<K, Long> toCountMap(List<Object[]> rows) {
        Map<K, Long> m = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                @SuppressWarnings("unchecked")
                K key = (K) row[0];
                m.put(key, ((Number) row[1]).longValue());
            }
        }
        return m;
    }

    private static <K> long count(Map<K, Long> m, K key) {
        return m.getOrDefault(key, 0L);
    }

    /**
     * Delete old logs (for data retention)
     */
    @Transactional
    public int deleteOldLogs(LocalDateTime before) {
        // Bulk DELETE — don't load every expired row into the persistence context first (OOM at scale).
        int count = smsLogRepository.bulkDeleteByCreatedAtBefore(before);
        log.info("Deleted {} old SMS logs before {}", count, before);
        return count;
    }
}
