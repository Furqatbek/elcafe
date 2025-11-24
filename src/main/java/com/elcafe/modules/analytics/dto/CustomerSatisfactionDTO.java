package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Customer satisfaction score aggregated from multiple sources
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSatisfactionDTO {

    private LocalDate startDate;

    private LocalDate endDate;

    // Overall satisfaction score (0-100)
    private Double overallSatisfactionScore;

    // Ratings by source
    private Double googleRating;

    private Integer googleReviewCount;

    private Double yandexRating;

    private Integer yandexReviewCount;

    private Double telegramRating;

    private Integer telegramReviewCount;

    private Double internalRating;

    private Integer internalReviewCount;

    // Aggregated metrics
    private Integer totalReviews;

    private Double averageRating;

    private Integer positiveReviews; // 4-5 stars

    private Integer neutralReviews; // 3 stars

    private Integer negativeReviews; // 1-2 stars

    private Double positivePercentage;

    private Double negativePercentage;
}
