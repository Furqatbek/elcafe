package com.elcafe.modules.promotion.dto;

import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreatePromotionRequest {

    @NotBlank(message = "Promotion name is required")
    private String name;

    private String description;

    @NotNull(message = "Promotion type is required")
    private PromotionType promotionType;

    @Builder.Default
    private PromotionScope promotionScope = PromotionScope.ALL;

    @NotNull(message = "Discount value is required")
    private BigDecimal discountValue;

    // For BUY_X_GET_Y promotions
    private Integer buyQuantity;
    private Integer getQuantity;

    // For FREE_ITEM promotions
    private Long freeProductId;

    @NotNull(message = "Start date is required")
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Integer priority = 0;

    @Builder.Default
    private Boolean stackable = false;

    // Rule configuration
    private PromotionRuleDTO rule;

    // Product/category inclusions/exclusions
    private List<PromotionProductDTO> promotionProducts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionRuleDTO {
        private BigDecimal minOrderAmount;
        private BigDecimal maxDiscountAmount;
        private Integer usageLimit;
        private Integer perCustomerLimit;
        private Integer minItems;
        private List<String> applicableOrderTypes; // DINE_IN, TAKEAWAY, DELIVERY
        private List<String> applicableDays; // MON, TUE, WED, THU, FRI, SAT, SUN
        private LocalTime startTime;
        private LocalTime endTime;
        @Builder.Default
        private Boolean firstOrderOnly = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionProductDTO {
        private Long productId;
        private Long categoryId;
        @Builder.Default
        private Boolean include = true;
    }
}
