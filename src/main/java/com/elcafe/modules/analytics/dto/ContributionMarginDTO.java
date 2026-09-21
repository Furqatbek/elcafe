package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Contribution margin per menu item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributionMarginDTO {

    private Long productId;

    private String productName;

    private String categoryName;

    private BigDecimal sellingPrice;

    private BigDecimal costPrice;

    private BigDecimal contributionMargin;

    private BigDecimal contributionMarginRatio;

    private Integer unitsSold;

    private BigDecimal totalContribution;

    private BigDecimal percentageOfTotalContribution;
}
