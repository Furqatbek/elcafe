package com.elcafe.modules.selfservice.exception;

/**
 * Thrown when order submission fails.
 */
public class OrderSubmissionException extends SelfServiceException {

    private final String reason;

    public OrderSubmissionException(String message) {
        super(message);
        this.reason = "UNKNOWN";
    }

    public OrderSubmissionException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    // Common factory methods
    public static OrderSubmissionException emptyCart() {
        return new OrderSubmissionException("EMPTY_CART", "Cannot submit order: Cart is empty");
    }

    public static OrderSubmissionException minimumNotMet(java.math.BigDecimal minimum, java.math.BigDecimal actual) {
        return new OrderSubmissionException("MINIMUM_NOT_MET",
                String.format("Minimum order amount not met: required %.2f, got %.2f", minimum, actual));
    }

    public static OrderSubmissionException missingCustomerDetails(String field) {
        return new OrderSubmissionException("MISSING_DETAILS",
                field + " is required for this order type");
    }

    public static OrderSubmissionException invalidPhone() {
        return new OrderSubmissionException("INVALID_PHONE", "Invalid phone number format");
    }

    public static OrderSubmissionException serviceNotConfigured() {
        return new OrderSubmissionException("NOT_CONFIGURED", "Self-service ordering is not configured for this restaurant");
    }
}
