package com.elcafe.modules.order.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Lifetime per-customer activity aggregate over ALL orders (no status filter — the RFM listing counts
 * cancelled orders too, matching its original per-customer walk). Replaces three queries per customer
 * (full order-history entities for count/recency, a SUM, and a distinct-sources query).
 */
public record CustomerActivityRow(Long customerId, Long orderCount, BigDecimal totalSpent,
                                  OffsetDateTime lastOrderAt) {
}
