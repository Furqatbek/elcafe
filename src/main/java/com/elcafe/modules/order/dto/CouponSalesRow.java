package com.elcafe.modules.order.dto;

import java.math.BigDecimal;

/** Per-coupon aggregate over non-cancelled orders carrying a coupon code. */
public record CouponSalesRow(String couponCode, Long redemptionCount, BigDecimal revenue, BigDecimal discount) {
}
