package com.elcafe.modules.promotion.dto;

import com.elcafe.modules.promotion.enums.DiscountType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyDiscountRequest {

    private String couponCode;

    private Long promotionId;

    private Long happyHourId;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    // For manual discounts
    private BigDecimal manualDiscountAmount;
    private BigDecimal manualDiscountPercent;
    private String discountReason;
}
