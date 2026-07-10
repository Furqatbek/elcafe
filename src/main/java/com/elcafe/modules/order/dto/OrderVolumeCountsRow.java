package com.elcafe.modules.order.dto;

/** Range-wide order counts over ALL statuses: total, revenue-status ("completed"), and cancelled. */
public record OrderVolumeCountsRow(Long totalOrders, Long completedOrders, Long cancelledOrders) {
}
