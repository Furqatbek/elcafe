package com.elcafe.modules.order.dto;

import com.elcafe.modules.order.enums.OrderStatus;

import java.time.OffsetDateTime;

/**
 * Scalar timing projection of one revenue-status order for OrderTimingAnalytics. Replaces loading the
 * order graph plus a per-order KitchenOrder lookup and a per-order status-history walk (three N+1s):
 * the kitchen prep minutes, the history row count, and the first occurrence of NEW/READY/DELIVERED all
 * arrive as correlated subselects in one query.
 *
 * @param deliveryInfoId non-null iff the order has delivery info ({@code getDeliveryInfo() != null})
 * @param historyCount   total status-history rows; 0 triggers the legacy created-at fallback
 */
public record OrderTimingRow(Long orderId, OrderStatus status, OffsetDateTime createdAt,
                             OffsetDateTime updatedAt, Long deliveryInfoId, OffsetDateTime actualDeliveryTime,
                             Integer kitchenPrepMinutes, Long historyCount,
                             OffsetDateTime newAt, OffsetDateTime readyAt, OffsetDateTime deliveredAt) {
}
