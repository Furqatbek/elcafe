package com.elcafe.modules.order.dto;

import com.elcafe.modules.order.enums.OrderSource;

/** One (customer, order source) pair; DISTINCT over all orders — batch form of the per-customer lookup. */
public record CustomerSourceRow(Long customerId, OrderSource orderSource) {
}
