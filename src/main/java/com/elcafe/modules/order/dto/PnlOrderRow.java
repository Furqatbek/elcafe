package com.elcafe.modules.order.dto;

import java.math.BigDecimal;

/** Scalar money projection of one revenue-qualifying order for the P&L report. */
public record PnlOrderRow(BigDecimal subtotal, BigDecimal serviceFee, BigDecimal deliveryFee,
                          BigDecimal tipAmount, BigDecimal discount, BigDecimal total, String discountType) {
}
