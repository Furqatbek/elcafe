package com.elcafe.modules.promotion.dto;

import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deserialized by Jackson through the no-args constructor and the setters only, so that
 * {@link #isProvided(String)} can tell an omitted field from an explicit null. No builder
 * or all-args constructor on purpose: those bypass the setters and would silently leave
 * the presence set empty.
 */
@Data
@NoArgsConstructor
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

    /**
     * Fields that were actually present in the request body. Needed for the nullable
     * fields: an explicit null means "clear this", an absent field means "leave it alone",
     * and without the distinction a cleared end date or free product can never be removed.
     */
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Set<String> providedFields = new HashSet<>();

    public boolean isProvided(String field) {
        return providedFields.contains(field);
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
        providedFields.add("endDate");
    }

    public void setFreeProductId(Long freeProductId) {
        this.freeProductId = freeProductId;
        providedFields.add("freeProductId");
    }
}
