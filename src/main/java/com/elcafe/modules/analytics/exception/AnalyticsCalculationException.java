package com.elcafe.modules.analytics.exception;

/**
 * Exception thrown when analytics calculations fail.
 * This is a recoverable exception that indicates partial data may be available.
 */
public class AnalyticsCalculationException extends RuntimeException {

    private final String metricName;
    private final Long restaurantId;
    private final boolean partialDataAvailable;

    public AnalyticsCalculationException(String message, String metricName) {
        super(message);
        this.metricName = metricName;
        this.restaurantId = null;
        this.partialDataAvailable = false;
    }

    public AnalyticsCalculationException(String message, String metricName, Long restaurantId) {
        super(message);
        this.metricName = metricName;
        this.restaurantId = restaurantId;
        this.partialDataAvailable = false;
    }

    public AnalyticsCalculationException(String message, String metricName, Long restaurantId,
                                         boolean partialDataAvailable) {
        super(message);
        this.metricName = metricName;
        this.restaurantId = restaurantId;
        this.partialDataAvailable = partialDataAvailable;
    }

    public AnalyticsCalculationException(String message, String metricName, Throwable cause) {
        super(message, cause);
        this.metricName = metricName;
        this.restaurantId = null;
        this.partialDataAvailable = false;
    }

    public AnalyticsCalculationException(String message, String metricName, Long restaurantId,
                                         boolean partialDataAvailable, Throwable cause) {
        super(message, cause);
        this.metricName = metricName;
        this.restaurantId = restaurantId;
        this.partialDataAvailable = partialDataAvailable;
    }

    public String getMetricName() {
        return metricName;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public boolean isPartialDataAvailable() {
        return partialDataAvailable;
    }
}
