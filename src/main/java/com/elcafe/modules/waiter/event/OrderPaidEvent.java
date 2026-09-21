package com.elcafe.modules.waiter.event;

import com.elcafe.modules.waiter.enums.OrderEventType;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Event fired when an order payment is completed
 */
@Getter
public class OrderPaidEvent extends WaiterEvent {

    private final String orderNumber;
    private final BigDecimal amount;
    private final String paymentMethod;
    private final String transactionId;

    public OrderPaidEvent(
            Object source,
            Long orderId,
            String orderNumber,
            Long tableId,
            Long waiterId,
            String triggeredBy,
            BigDecimal amount,
            String paymentMethod,
            String transactionId) {
        super(source, OrderEventType.PAYMENT_COMPLETED, orderId, tableId, waiterId, triggeredBy);
        this.orderNumber = orderNumber;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.transactionId = transactionId;

        addMetadata("amount", amount);
        addMetadata("paymentMethod", paymentMethod);
        addMetadata("transactionId", transactionId);
    }

    @Override
    public String getEventDescription() {
        return String.format("Payment of $%.2f completed for order %s - Method: %s, Transaction: %s",
                amount, orderNumber, paymentMethod, transactionId);
    }
}
