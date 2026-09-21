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
public class UpdatePromotionRequest {

    private String name;
    private String description;
    private PromotionType promotionType;
    private PromotionScope promotionScope;
    private BigDecimal discountValue;
    private Integer buyQuantity;
    private Integer getQuantity;
    private Long freeProductId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean active;
    private Integer priority;
    private Boolean stackable;
    private CreatePromotionRequest.PromotionRuleDTO rule;
    private List<CreatePromotionRequest.PromotionProductDTO> promotionProducts;
}
