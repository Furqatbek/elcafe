package com.elcafe.modules.instagram.dto;

/**
 * Tenant-scoped Instagram statistics for the operator dashboard: subscriber counts plus the {@code
 * instagram_logs} (V171) message-status breakdown — the payoff that foundation was laid for.
 *
 * <p>Mirrors the SHAPE of {@code TelegramSubscriberService#getStatistics()}'s {@code
 * Map<String,Object>} (same {@code totalSubscribers}/{@code activeSubscribers}/{@code
 * newThisWeek}/{@code newThisMonth} fields) but typed, and — unlike Telegram's version, whose
 * subscriber counts carry no restaurant predicate at all — every count behind this record is
 * explicitly scoped to one restaurant: Instagram has been a per-tenant channel since birth (V163),
 * so a decoy row under a second restaurant must never leak into another tenant's numbers.
 *
 * @param totalSubscribers      every subscriber row for this tenant, in any state
 * @param activeSubscribers     {@code isActive = true}
 * @param registeredSubscribers active, non-blocked, {@code conversationState = REGISTERED} — from
 *                              {@link com.elcafe.modules.instagram.repository.InstagramSubscriberRepository#countRegistered}
 * @param blockedSubscribers    {@code isBlocked = true}
 * @param newThisWeek           subscriber rows created in the last 7 days
 * @param newThisMonth          subscriber rows created in the last 30 days
 * @param totalMessages         every {@code instagram_logs} row for this tenant, in any status
 * @param sentMessages          {@code MessageStatus.SENT}
 * @param deliveredMessages     {@code MessageStatus.DELIVERED}
 * @param failedMessages        {@code MessageStatus.FAILED}
 * @param pendingMessages       {@code MessageStatus.PENDING}
 */
public record InstagramStatisticsResponse(
        long totalSubscribers,
        long activeSubscribers,
        long registeredSubscribers,
        long blockedSubscribers,
        long newThisWeek,
        long newThisMonth,
        long totalMessages,
        long sentMessages,
        long deliveredMessages,
        long failedMessages,
        long pendingMessages) {
}
