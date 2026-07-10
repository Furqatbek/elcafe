package com.elcafe.modules.order.dto;

import java.math.BigDecimal;

/** Range-wide revenue totals over revenue-qualifying orders, aggregated in the database. */
public record RevenueTotalsRow(BigDecimal totalRevenue, Long orderCount) {
}
