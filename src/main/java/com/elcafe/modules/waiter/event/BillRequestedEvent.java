package com.elcafe.modules.waiter.event;

import com.elcafe.modules.waiter.enums.OrderEventType;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Event fired when a customer requests the bill
 */
@Getter
public class BillRequestedEvent extends WaiterEvent {

    private final String orderNumber;
    private final BigDecimal totalAmount;
    private final String paymentMethod;

    public BillRequestedEvent(
            Object source,
            Long orderId,
            String orderNumber,
            Long tableId,
            Long waiterId,
            String triggeredBy,
            BigDecimal totalAmount,
            String paymentMethod) {
        super(source, OrderEventType.BILL_REQUESTED, orderId, tableId, waiterId, triggeredBy);
        this.orderNumber = orderNumber;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
    }

    @Override
    public String getEventDescription() {
        return String.format("Bill requested for order %s at table %d - Total: $%.2f, Payment: %s",
                orderNumber, getTableId(), totalAmount, paymentMethod);
    }
}
