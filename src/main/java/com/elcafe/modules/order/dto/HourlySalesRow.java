package com.elcafe.modules.order.dto;

import java.math.BigDecimal;

/** Per-hour revenue/count aggregate over revenue-status orders (OperationalAnalytics sales-per-hour). */
public record HourlySalesRow(Integer hour, BigDecimal revenue, Long orderCount) {
}
