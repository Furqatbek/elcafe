package com.elcafe.modules.order.dto;

import com.elcafe.modules.order.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Scalar projection of one revenue-qualifying order, used by analytics instead of materializing the
 * full {@code Order} entity graph (items, payments, associations). A year of orders as these rows is
 * a few MB; as entities it OOMed the heap (audit PERF-2).
 *
 * @param firstPaymentMethod method of the order's first payment (lowest payment id — the deterministic
 *                           equivalent of {@code Order.getPayment()}), or null if the order has none
 */
public record RevenueOrderRow(OffsetDateTime createdAt, BigDecimal total, PaymentMethod firstPaymentMethod) {
}
