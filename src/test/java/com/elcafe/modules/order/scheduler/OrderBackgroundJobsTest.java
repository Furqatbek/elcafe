package com.elcafe.modules.order.scheduler;

import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.waiter.repository.OrderEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the order-event retention cleanup: gated OFF by default (no delete), a non-positive horizon
 * is treated as disabled, and when enabled the bulk-delete runs with a cutoff at the configured
 * number of days before now.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderBackgroundJobsTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private OrderEventRepository orderEventRepository;

    @InjectMocks private OrderBackgroundJobs jobs;

    private void configure(boolean enabled, int days) {
        ReflectionTestUtils.setField(jobs, "eventRetentionEnabled", enabled);
        ReflectionTestUtils.setField(jobs, "eventRetentionDays", days);
    }

    @Test
    void cleanup_disabledByDefault_deletesNothing() {
        configure(false, 90);

        jobs.cleanupOldData();

        verify(orderEventRepository, never()).deleteOlderThan(any());
        verifyNoInteractions(orderRepository, orderService);
    }

    @Test
    void cleanup_nonPositiveHorizon_isTreatedAsDisabled() {
        configure(true, 0);

        jobs.cleanupOldData();

        verify(orderEventRepository, never()).deleteOlderThan(any());
    }

    @Test
    void cleanup_enabled_deletesEventsOlderThanConfiguredHorizon() {
        configure(true, 90);
        when(orderEventRepository.deleteOlderThan(any())).thenReturn(7);

        LocalDateTime before = LocalDateTime.now().minusDays(90);
        jobs.cleanupOldData();
        LocalDateTime after = LocalDateTime.now().minusDays(90);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderEventRepository).deleteOlderThan(cutoff.capture());

        // Cutoff is computed as now().minusDays(90) inside the job, so it must land between the
        // two reference points captured around the call.
        assertThat(cutoff.getValue())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    void cleanup_swallowsRepositoryFailure() {
        configure(true, 90);
        when(orderEventRepository.deleteOlderThan(any())).thenThrow(new RuntimeException("db down"));

        // The job must not propagate — a failed sweep should be logged, not crash the scheduler.
        jobs.cleanupOldData();

        verify(orderEventRepository).deleteOlderThan(any());
    }
}
