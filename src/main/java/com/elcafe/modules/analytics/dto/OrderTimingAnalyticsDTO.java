package com.elcafe.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Order preparation and delivery timing analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimingAnalyticsDTO {

    private LocalDate startDate;

    private LocalDate endDate;

    // Preparation time metrics
    private Double averagePreparationTimeMinutes;

    private Double medianPreparationTimeMinutes;

    private Double minPreparationTimeMinutes;

    private Double maxPreparationTimeMinutes;

    // Dine-in wait time metrics
    private Double averageDineInWaitTimeMinutes;

    private Double medianDineInWaitTimeMinutes;

    // Delivery time metrics
    private Double averageDeliveryTimeMinutes;

    private Double medianDeliveryTimeMinutes;

    private Double minDeliveryTimeMinutes;

    private Double maxDeliveryTimeMinutes;

    // Percentage of orders meeting targets
    private Double percentagePreparationUnder15Min;

    private Double percentageDeliveryUnder30Min;

    private Integer totalOrdersAnalyzed;
}
