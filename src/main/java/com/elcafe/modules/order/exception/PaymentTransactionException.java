package com.elcafe.modules.order.exception;

/**
 * Exception thrown when a payment transaction fails and requires rollback.
 * This exception is designed to trigger transaction rollback in @Transactional methods.
 */
public class PaymentTransactionException extends RuntimeException {

    private final Long orderId;
    private final Long paymentId;
    private final String transactionId;
    private final PaymentFailureReason reason;

    public PaymentTransactionException(String message) {
        super(message);
        this.orderId = null;
        this.paymentId = null;
        this.transactionId = null;
        this.reason = PaymentFailureReason.UNKNOWN;
    }

    public PaymentTransactionException(String message, Throwable cause) {
        super(message, cause);
        this.orderId = null;
        this.paymentId = null;
        this.transactionId = null;
        this.reason = PaymentFailureReason.UNKNOWN;
    }

    public PaymentTransactionException(String message, PaymentFailureReason reason) {
        super(message);
        this.orderId = null;
        this.paymentId = null;
        this.transactionId = null;
        this.reason = reason;
    }

    public PaymentTransactionException(String message, Long orderId, PaymentFailureReason reason) {
        super(message);
        this.orderId = orderId;
        this.paymentId = null;
        this.transactionId = null;
        this.reason = reason;
    }

    public PaymentTransactionException(String message, Long orderId, PaymentFailureReason reason, Throwable cause) {
        super(message, cause);
        this.orderId = orderId;
        this.paymentId = null;
        this.transactionId = null;
        this.reason = reason;
    }

    public PaymentTransactionException(String message, Long orderId, String transactionId,
                                       PaymentFailureReason reason, Throwable cause) {
        super(message, cause);
        this.orderId = orderId;
        this.paymentId = null;
        this.transactionId = transactionId;
        this.reason = reason;
    }

    public PaymentTransactionException(String message, Long orderId, Long paymentId,
                                       String transactionId, PaymentFailureReason reason) {
        super(message);
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.transactionId = transactionId;
        this.reason = reason;
    }

    public PaymentTransactionException(String message, Long orderId, Long paymentId,
                                       String transactionId, PaymentFailureReason reason, Throwable cause) {
        super(message, cause);
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.transactionId = transactionId;
        this.reason = reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public PaymentFailureReason getReason() {
        return reason;
    }

    public enum PaymentFailureReason {
        GATEWAY_ERROR,
        INSUFFICIENT_FUNDS,
        CARD_DECLINED,
        INVALID_CARD,
        FRAUD_DETECTED,
        NETWORK_ERROR,
        ORDER_STATUS_INVALID,
        ORDER_ALREADY_PAID,
        INVALID_AMOUNT,
        REFUND_FAILED,
        DATABASE_ERROR,
        CONCURRENT_MODIFICATION,
        UNKNOWN
    }
}
