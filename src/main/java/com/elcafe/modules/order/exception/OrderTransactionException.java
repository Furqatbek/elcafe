package com.elcafe.modules.order.exception;

import com.elcafe.modules.order.enums.OrderStatus;

/**
 * Exception thrown when an order transaction fails and requires rollback.
 * This exception is designed to trigger transaction rollback in @Transactional methods.
 */
public class OrderTransactionException extends RuntimeException {

    private final Long orderId;
    private final OrderStatus fromStatus;
    private final OrderStatus toStatus;
    private final OrderFailureReason reason;

    public OrderTransactionException(String message) {
        super(message);
        this.orderId = null;
        this.fromStatus = null;
        this.toStatus = null;
        this.reason = OrderFailureReason.UNKNOWN;
    }

    public OrderTransactionException(String message, Throwable cause) {
        super(message, cause);
        this.orderId = null;
        this.fromStatus = null;
        this.toStatus = null;
        this.reason = OrderFailureReason.UNKNOWN;
    }

    public OrderTransactionException(String message, Long orderId, OrderFailureReason reason) {
        super(message);
        this.orderId = orderId;
        this.fromStatus = null;
        this.toStatus = null;
        this.reason = reason;
    }

    public OrderTransactionException(String message, Long orderId, OrderStatus fromStatus,
                                     OrderStatus toStatus, OrderFailureReason reason) {
        super(message);
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
    }

    public OrderTransactionException(String message, Long orderId, OrderStatus fromStatus,
                                     OrderStatus toStatus, OrderFailureReason reason, Throwable cause) {
        super(message, cause);
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public OrderFailureReason getReason() {
        return reason;
    }

    public enum OrderFailureReason {
        INVALID_STATUS_TRANSITION,
        ORDER_NOT_FOUND,
        INSUFFICIENT_INVENTORY,
        INVENTORY_DEDUCTION_FAILED,
        KITCHEN_ORDER_CREATION_FAILED,
        TABLE_ASSIGNMENT_FAILED,
        TABLE_RELEASE_FAILED,
        CONCURRENT_MODIFICATION,
        DATABASE_ERROR,
        UNKNOWN
    }
}
