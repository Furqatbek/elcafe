package com.elcafe.modules.sms.repository;

import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.sms.enums.SmsMessageType;
import com.elcafe.modules.sms.service.SmsLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the SMS-statistics mapping now built from DB-side GROUP BY / SUM aggregates instead of loading
 * every log row and counting in Java (audit PERF-4), and that retention delete uses the bulk query
 * (PERF-12). The aggregate JPQL itself (standard COUNT/GROUP BY/SUM, mirroring the existing
 * getStatusCountsSince/getTypeCountsSince) runs against real Postgres in the CI migrations job; here we
 * pin the service's row-to-map translation, which is the behavior that changed.
 */
@ExtendWith(MockitoExtension.class)
class SmsLogAggregateTest {

    @Mock private SmsLogRepository repo;
    @InjectMocks private SmsLogService service;

    private final LocalDateTime from = LocalDateTime.now().minusDays(30);
    private final LocalDateTime to = LocalDateTime.now();

    @Test
    @DisplayName("getStatistics maps status/type group-counts and cost without loading rows")
    void getStatistics_fromAggregates() {
        when(repo.getStatusCountsBetween(from, to)).thenReturn(List.of(
                new Object[]{MessageStatus.DELIVERED, 2L},
                new Object[]{MessageStatus.FAILED, 1L},
                new Object[]{MessageStatus.PENDING, 1L},
                new Object[]{MessageStatus.SENT, 1L}));
        when(repo.getTypeCountsBetween(from, to)).thenReturn(List.of(
                new Object[]{SmsMessageType.CAMPAIGN, 2L},
                new Object[]{SmsMessageType.AUTOMATION, 1L}));
        when(repo.getTotalCostBetween(from, to)).thenReturn(new BigDecimal("3.00"));

        Map<String, Object> stats = service.getStatistics(from, to);

        assertThat(stats.get("totalSent")).isEqualTo(5L);   // 2 + 1 + 1 + 1
        assertThat(stats.get("delivered")).isEqualTo(2L);
        assertThat(stats.get("failed")).isEqualTo(1L);
        assertThat(stats.get("pending")).isEqualTo(2L);   // PENDING(1) + QUEUED(0) + SENT(1)
        assertThat(stats.get("campaignMessages")).isEqualTo(2L);
        assertThat(stats.get("automationMessages")).isEqualTo(1L);
        assertThat(stats.get("totalCost")).isEqualTo(3.0);
        assertThat((double) stats.get("deliveryRate")).isEqualTo(40.0);   // 2/5
        // crucially, it never materializes the rows
        verify(repo, org.mockito.Mockito.never()).findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("deleteOldLogs uses the bulk DELETE, not load-then-deleteAll")
    void deleteOldLogs_bulk() {
        when(repo.bulkDeleteByCreatedAtBefore(from)).thenReturn(7);
        assertThat(service.deleteOldLogs(from)).isEqualTo(7);
        verify(repo).bulkDeleteByCreatedAtBefore(from);
        verify(repo, org.mockito.Mockito.never()).findByCreatedAtBefore(any());
    }
}
