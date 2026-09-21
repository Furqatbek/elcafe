package com.elcafe.modules.promotion.dto;

import com.elcafe.modules.promotion.enums.PromotionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateCouponResponse {

    private Boolean valid;
    private String code;
    private Long promotionId;
    private String promotionName;
    private PromotionType promotionType;
    private BigDecimal discountValue;
    private BigDecimal calculatedDiscount;
    private String errorMessage;

    // For FREE_ITEM promotions
    private String freeProductName;
    private Long freeProductId;
    private BigDecimal freeProductPrice;

    // For BUY_X_GET_Y promotions
    private Integer buyQuantity;
    private Integer getQuantity;

    public static ValidateCouponResponse invalid(String errorMessage) {
        return ValidateCouponResponse.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .build();
    }

    public static ValidateCouponResponse valid(String code, Long promotionId, String promotionName,
                                               PromotionType type, BigDecimal discountValue,
                                               BigDecimal calculatedDiscount) {
        return ValidateCouponResponse.builder()
                .valid(true)
                .code(code)
                .promotionId(promotionId)
                .promotionName(promotionName)
                .promotionType(type)
                .discountValue(discountValue)
                .calculatedDiscount(calculatedDiscount)
                .build();
    }
}
