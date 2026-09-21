package com.elcafe.modules.promotion.dto;

import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String name;
    private String description;
    private PromotionType promotionType;
    private PromotionScope promotionScope;
    private BigDecimal discountValue;
    private Integer buyQuantity;
    private Integer getQuantity;
    private Long freeProductId;
    private String freeProductName;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean active;
    private Boolean currentlyValid;
    private Integer priority;
    private Boolean stackable;
    private PromotionRuleResponse rule;
    private List<PromotionProductResponse> promotionProducts;
    private Long totalUsage;
    private BigDecimal totalDiscountGiven;
    private Long couponCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionRuleResponse {
        private Long id;
        private BigDecimal minOrderAmount;
        private BigDecimal maxDiscountAmount;
        private Integer usageLimit;
        private Integer perCustomerLimit;
        private Integer minItems;
        private List<String> applicableOrderTypes;
        private List<String> applicableDays;
        private LocalTime startTime;
        private LocalTime endTime;
        private Boolean firstOrderOnly;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionProductResponse {
        private Long id;
        private Long productId;
        private String productName;
        private Long categoryId;
        private String categoryName;
        private Boolean include;
    }
}
