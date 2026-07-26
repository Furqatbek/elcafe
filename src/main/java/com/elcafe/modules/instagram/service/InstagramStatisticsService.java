package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.instagram.dto.InstagramStatisticsResponse;
import com.elcafe.modules.instagram.repository.InstagramLogRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.sms.enums.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tenant-scoped Instagram statistics: subscriber counts (mirrors the SHAPE of {@code
 * TelegramSubscriberService#getStatistics()}) plus the {@code instagram_logs} (V171) message-status
 * breakdown — the payoff that foundation was laid for. {@code InstagramSubscriber.countRegistered()}
 * previously had no caller; this is that caller.
 *
 * <p>A dedicated service rather than a method bolted onto {@link InstagramBotService}: that service
 * (and every other Instagram write path) is out of scope for this change, and a statistics read only
 * needs the two repositories below — no reason to widen an already-large service's dependency set.
 *
 * <p>Unlike Telegram's version — whose subscriber counts carry no restaurant predicate at all, a
 * pre-V163 carry-over the class-level javadoc there still calls out — every query here is explicitly
 * scoped by {@code restaurantId}: Instagram has been a per-tenant channel since birth, so a decoy row
 * under a second restaurant must never leak into another tenant's numbers.
 *
 * <p>Unlike the list/search reads elsewhere in this module (which let a SUPER_ADMIN fall through to
 * an unscoped, cross-tenant view — e.g. {@code InstagramBotConfigService#getAll}), a null tenant here
 * is rejected outright: a single flat statistics payload has no slot for "every restaurant's numbers
 * at once", so — like {@code InstagramBotService#searchSubscribers} — a platform account is told to
 * sign in as a restaurant instead of silently getting an all-zero or ill-defined aggregate response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramStatisticsService {

    private final InstagramSubscriberRepository subscriberRepository;
    private final InstagramLogRepository logRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @Transactional(readOnly = true)
    public InstagramStatisticsResponse getStatistics() {
        Long restaurantId = requireTenant();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime weekAgo = now.minusWeeks(1);
        OffsetDateTime monthAgo = now.minusMonths(1);

        long total = subscriberRepository.countByRestaurantId(restaurantId);
        long active = subscriberRepository.countByRestaurantIdAndIsActiveTrue(restaurantId);
        long registered = subscriberRepository.countRegistered(restaurantId);
        long blocked = subscriberRepository.countByRestaurantIdAndIsBlockedTrue(restaurantId);
        long newThisWeek = subscriberRepository.countByRestaurantIdAndCreatedAtAfter(restaurantId, weekAgo);
        long newThisMonth = subscriberRepository.countByRestaurantIdAndCreatedAtAfter(restaurantId, monthAgo);

        // Single grouped query instead of one countByRestaurantIdAndStatus() call per bucket — the
        // same "aggregate in the DB, don't loop status values" reasoning as SmsLogService#getStatistics.
        Map<MessageStatus, Long> byStatus = toCountMap(logRepository.getStatusCountsByRestaurantId(restaurantId));
        long totalMessages = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long sent = byStatus.getOrDefault(MessageStatus.SENT, 0L);
        long delivered = byStatus.getOrDefault(MessageStatus.DELIVERED, 0L);
        long failed = byStatus.getOrDefault(MessageStatus.FAILED, 0L);
        long pending = byStatus.getOrDefault(MessageStatus.PENDING, 0L);

        return new InstagramStatisticsResponse(
                total, active, registered, blocked, newThisWeek, newThisMonth,
                totalMessages, sent, delivered, failed, pending);
    }

    /**
     * A per-tenant channel's statistics need exactly one restaurant to count against. A SUPER_ADMIN
     * (platform operator — {@code currentTenantReadScopeStrict()} returns null) is rejected rather
     * than silently handed an all-zero or cross-tenant-aggregate response; mirrors {@code
     * InstagramBotService#searchSubscribers}'s identical guard on a different read.
     */
    private Long requireTenant() {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        if (tenant == null) {
            throw new BadRequestException(
                    "Instagram statistics belong to a restaurant. Sign in with a restaurant-scoped "
                            + "account to view them.");
        }
        return tenant;
    }

    private static Map<MessageStatus, Long> toCountMap(List<Object[]> rows) {
        Map<MessageStatus, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                counts.put((MessageStatus) row[0], ((Number) row[1]).longValue());
            }
        }
        return counts;
    }
}
