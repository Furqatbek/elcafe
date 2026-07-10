package com.elcafe.modules.order.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Scalar discount projection of one paid order (promotion analytics + daily discount trends). */
public record DiscountOrderRow(OffsetDateTime createdAt, BigDecimal total, BigDecimal discount,
                               String discountType) {
}
