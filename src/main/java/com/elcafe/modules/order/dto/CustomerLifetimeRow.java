package com.elcafe.modules.order.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Lifetime per-customer aggregate over revenue-status orders (LTV). Replaces the worst loader in the
 * codebase: every active customer's entire order history materialized one customer at a time.
 */
public record CustomerLifetimeRow(Long customerId, BigDecimal totalSpent, Long orderCount,
                                  OffsetDateTime firstOrderAt, OffsetDateTime lastOrderAt) {
}
