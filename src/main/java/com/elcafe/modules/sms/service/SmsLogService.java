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
        Map<String, Object> stats = new HashMap<>();

        List<SmsLog> logs = smsLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);

        long totalSent = logs.size();
        long delivered = logs.stream().filter(l -> l.getStatus() == MessageStatus.DELIVERED).count();
        long failed = logs.stream().filter(l -> l.getStatus() == MessageStatus.FAILED || l.getStatus() == MessageStatus.REJECTED).count();
        long pending = logs.stream().filter(l -> l.getStatus() == MessageStatus.PENDING || l.getStatus() == MessageStatus.QUEUED || l.getStatus() == MessageStatus.SENT).count();

        // Count by type
        long campaignCount = logs.stream().filter(l -> l.getMessageType() == SmsMessageType.CAMPAIGN).count();
        long automationCount = logs.stream().filter(l -> l.getMessageType() == SmsMessageType.AUTOMATION).count();
        long transactionalCount = logs.stream().filter(l -> l.getMessageType() == SmsMessageType.TRANSACTIONAL).count();
        long manualCount = logs.stream().filter(l -> l.getMessageType() == SmsMessageType.MANUAL).count();

        // Calculate total cost
        double totalCost = logs.stream()
                .filter(l -> l.getCost() != null)
                .mapToDouble(l -> l.getCost().doubleValue())
                .sum();

        stats.put("totalSent", totalSent);
        stats.put("delivered", delivered);
        stats.put("failed", failed);
        stats.put("pending", pending);
        stats.put("deliveryRate", totalSent > 0 ? (delivered * 100.0 / totalSent) : 0);
        stats.put("campaignMessages", campaignCount);
        stats.put("automationMessages", automationCount);
        stats.put("transactionalMessages", transactionalCount);
        stats.put("manualMessages", manualCount);
        stats.put("totalCost", totalCost);

        return stats;
    }

    /**
     * Delete old logs (for data retention)
     */
    @Transactional
    public int deleteOldLogs(LocalDateTime before) {
        List<SmsLog> oldLogs = smsLogRepository.findByCreatedAtBefore(before);
        int count = oldLogs.size();
        smsLogRepository.deleteAll(oldLogs);
        log.info("Deleted {} old SMS logs before {}", count, before);
        return count;
    }
}
