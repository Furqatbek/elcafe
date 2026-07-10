package com.elcafe.modules.order.dto;

import java.time.OffsetDateTime;

/** Per-customer order count over revenue-status orders in a range, with the customer's creation time. */
public record CustomerOrderStatsRow(Long customerId, Long orderCount, OffsetDateTime customerCreatedAt) {
}
