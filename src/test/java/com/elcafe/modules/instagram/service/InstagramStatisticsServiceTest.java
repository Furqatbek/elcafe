package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.instagram.dto.InstagramStatisticsResponse;
import com.elcafe.modules.instagram.repository.InstagramLogRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.sms.enums.MessageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InstagramStatisticsService}: assembles subscriber + {@code instagram_logs} message-status
 * stats scoped to the caller's own restaurant, and refuses a platform (SUPER_ADMIN) account outright
 * rather than handing back an all-zero or cross-tenant-aggregate response. Mirrors the Mockito style
 * of {@code InstagramTemplateServiceTest} / {@code InstagramBotServiceTenantIsolationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramStatisticsServiceTest {

    private static final Long TENANT = 1L;

    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramLogRepository logRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;

    @InjectMocks private InstagramStatisticsService service;

    private void stubZeroSubscriberCounts() {
        when(subscriberRepository.countByRestaurantId(TENANT)).thenReturn(0L);
        when(subscriberRepository.countByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(0L);
        when(subscriberRepository.countRegistered(TENANT)).thenReturn(0L);
        when(subscriberRepository.countByRestaurantIdAndIsBlockedTrue(TENANT)).thenReturn(0L);
        when(subscriberRepository.countByRestaurantIdAndCreatedAtAfter(eq(TENANT), any(OffsetDateTime.class)))
                .thenReturn(0L);
    }

    @Test
    @DisplayName("assembles subscriber + message-status stats, scoped to the caller's own restaurant")
    void assemblesStatisticsForCallersOwnRestaurant() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(subscriberRepository.countByRestaurantId(TENANT)).thenReturn(10L);
        when(subscriberRepository.countByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(8L);
        when(subscriberRepository.countRegistered(TENANT)).thenReturn(5L);
        when(subscriberRepository.countByRestaurantIdAndIsBlockedTrue(TENANT)).thenReturn(2L);
        // Two calls happen in sequence — newThisWeek first, then newThisMonth (pinned separately
        // by newSubscriberCutoffsAreWeekAndMonth below, so this ordering assumption is guarded).
        when(subscriberRepository.countByRestaurantIdAndCreatedAtAfter(eq(TENANT), any(OffsetDateTime.class)))
                .thenReturn(3L, 4L);
        when(logRepository.getStatusCountsByRestaurantId(TENANT)).thenReturn(List.of(
                new Object[]{MessageStatus.SENT, 20L},
                new Object[]{MessageStatus.FAILED, 5L},
                new Object[]{MessageStatus.DELIVERED, 7L},
                new Object[]{MessageStatus.PENDING, 1L}
        ));

        InstagramStatisticsResponse stats = service.getStatistics();

        assertThat(stats.totalSubscribers()).isEqualTo(10L);
        assertThat(stats.activeSubscribers()).isEqualTo(8L);
        assertThat(stats.registeredSubscribers()).isEqualTo(5L);
        assertThat(stats.blockedSubscribers()).isEqualTo(2L);
        assertThat(stats.newThisWeek()).isEqualTo(3L);
        assertThat(stats.newThisMonth()).isEqualTo(4L);
        assertThat(stats.totalMessages()).isEqualTo(33L); // 20 + 5 + 7 + 1, incl. buckets not named below
        assertThat(stats.sentMessages()).isEqualTo(20L);
        assertThat(stats.deliveredMessages()).isEqualTo(7L);
        assertThat(stats.failedMessages()).isEqualTo(5L);
        assertThat(stats.pendingMessages()).isEqualTo(1L);

        verify(subscriberRepository).countByRestaurantId(TENANT);
        verify(subscriberRepository).countByRestaurantIdAndIsActiveTrue(TENANT);
        verify(subscriberRepository).countRegistered(TENANT);
        verify(subscriberRepository).countByRestaurantIdAndIsBlockedTrue(TENANT);
        verify(logRepository).getStatusCountsByRestaurantId(TENANT);
    }

    @Test
    @DisplayName("newThisWeek/newThisMonth use 7-day and 30-day cutoffs respectively")
    void newSubscriberCutoffsAreWeekAndMonth() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        stubZeroSubscriberCounts();
        when(logRepository.getStatusCountsByRestaurantId(TENANT)).thenReturn(List.of());

        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
        service.getStatistics();

        ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(subscriberRepository, times(2))
                .countByRestaurantIdAndCreatedAtAfter(eq(TENANT), captor.capture());
        List<OffsetDateTime> cutoffs = captor.getAllValues();

        assertThat(cutoffs.get(0)).isAfter(before.minusDays(8)).isBefore(before.minusDays(6));
        assertThat(cutoffs.get(1)).isAfter(before.minusDays(31)).isBefore(before.minusDays(29));
    }

    @Test
    @DisplayName("message-status buckets default to zero when a status has no rows yet")
    void missingStatusBucketsDefaultToZero() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        stubZeroSubscriberCounts();
        when(logRepository.getStatusCountsByRestaurantId(TENANT)).thenReturn(List.of());

        InstagramStatisticsResponse stats = service.getStatistics();

        assertThat(stats.totalMessages()).isZero();
        assertThat(stats.sentMessages()).isZero();
        assertThat(stats.deliveredMessages()).isZero();
        assertThat(stats.failedMessages()).isZero();
        assertThat(stats.pendingMessages()).isZero();
    }

    @Test
    @DisplayName("totalMessages sums every status bucket returned, not just the four named ones")
    void totalMessagesSumsEveryStatusIncludingUnnamedOnes() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        stubZeroSubscriberCounts();
        // QUEUED/REJECTED have no dedicated named field, but must still count toward the total —
        // otherwise totalMessages could silently undercount instagram_logs rows.
        when(logRepository.getStatusCountsByRestaurantId(TENANT)).thenReturn(List.of(
                new Object[]{MessageStatus.SENT, 2L},
                new Object[]{MessageStatus.QUEUED, 3L},
                new Object[]{MessageStatus.REJECTED, 1L}
        ));

        InstagramStatisticsResponse stats = service.getStatistics();

        assertThat(stats.totalMessages()).isEqualTo(6L);
        assertThat(stats.sentMessages()).isEqualTo(2L);
    }

    @Test
    @DisplayName("a platform (SUPER_ADMIN) account cannot pull statistics — it must act as a restaurant")
    void superAdminCannotGetStatistics() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(null);

        assertThatThrownBy(() -> service.getStatistics())
                .isInstanceOf(BadRequestException.class);
        verify(subscriberRepository, never()).countByRestaurantId(anyLong());
        verify(subscriberRepository, never()).countByRestaurantIdAndIsActiveTrue(anyLong());
        verify(subscriberRepository, never()).countRegistered(anyLong());
        verify(subscriberRepository, never()).countByRestaurantIdAndIsBlockedTrue(anyLong());
        verify(subscriberRepository, never()).countByRestaurantIdAndCreatedAtAfter(anyLong(), any());
        verify(logRepository, never()).getStatusCountsByRestaurantId(anyLong());
    }
}
