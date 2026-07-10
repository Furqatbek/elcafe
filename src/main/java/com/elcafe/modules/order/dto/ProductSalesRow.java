package com.elcafe.modules.order.dto;

import java.math.BigDecimal;

/**
 * Per-product sales aggregate over revenue-qualifying orders (SUM of item line totals and quantities,
 * grouped by product in the database). Replaces streaming every {@code OrderItem} of every order into
 * the JVM for category/COGS/margin analytics (audit PERF-2).
 */
public record ProductSalesRow(Long productId, BigDecimal revenue, Long quantitySold) {
}
