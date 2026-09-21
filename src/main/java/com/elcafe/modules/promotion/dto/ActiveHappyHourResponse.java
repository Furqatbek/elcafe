package com.elcafe.modules.promotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lightweight response for active happy hours, used for POS and menu display
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveHappyHourResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal discountPercent;
    private String endsAt; // HH:mm format - when the current happy hour ends
    private List<Long> applicableProductIds;
    private List<Long> applicableCategoryIds;
    private Boolean appliesToAll; // true if no specific products/categories
}
