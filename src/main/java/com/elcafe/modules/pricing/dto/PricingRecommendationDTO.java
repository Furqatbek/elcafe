package com.elcafe.modules.pricing.dto;

import com.elcafe.modules.pricing.enums.PricingStrategy;
import com.elcafe.modules.pricing.enums.RecommendationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for pricing recommendations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingRecommendationDTO {

    private Long productId;
    private String productName;
    private String categoryName;

    // Current pricing
    private BigDecimal currentPrice;
    private BigDecimal currentCostPrice;
    private BigDecimal currentMargin;
    private BigDecimal currentMarginPercentage;

    // Recommended pricing
    private BigDecimal recommendedPrice;
    private BigDecimal projectedMargin;
    private BigDecimal projectedMarginPercentage;
    private BigDecimal priceChange;
    private BigDecimal priceChangePercentage;

    // Strategy and rationale
    private PricingStrategy strategy;
    private RecommendationType recommendationType;
    private String rationale;
    private Integer confidenceScore; // 0-100

    // Sales context
    private Long unitsSoldLast30Days;
    private BigDecimal revenueLast30Days;
    private BigDecimal avgOrderFrequency;

    // Competitor context (if available)
    private BigDecimal marketAveragePrice;
    private BigDecimal competitorMinPrice;
    private BigDecimal competitorMaxPrice;

    private LocalDateTime generatedAt;
}
