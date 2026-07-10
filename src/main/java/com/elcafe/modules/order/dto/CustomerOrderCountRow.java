package com.elcafe.modules.order.dto;

/** Per-customer order count over ALL orders in a range (satisfaction repeat-rate). */
public record CustomerOrderCountRow(Long customerId, Long orderCount) {
}
