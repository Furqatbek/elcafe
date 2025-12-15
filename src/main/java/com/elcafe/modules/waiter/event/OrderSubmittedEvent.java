package com.elcafe.modules.waiter.event;

import com.elcafe.modules.waiter.enums.OrderEventType;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Event fired when a waiter submits an order to the kitchen
 */
@Getter
public class OrderSubmittedEvent extends WaiterEvent {

    private final String orderNumber;
    private final BigDecimal totalAmount;
    private final Integer itemCount;

    public OrderSubmittedEvent(
            Object source,
            Long orderId,
            String orderNumber,
            Long tableId,
            Long waiterId,
            String triggeredBy,
            BigDecimal totalAmount,
            Integer itemCount) {
        super(source, OrderEventType.ORDER_SUBMITTED, orderId, tableId, waiterId, triggeredBy);
        this.orderNumber = orderNumber;
        this.totalAmount = totalAmount;
        this.itemCount = itemCount;
    }

    @Override
    public String getEventDescription() {
        return String.format("Order %s submitted to kitchen by %s - Total: $%.2f, Items: %d",
                orderNumber, getTriggeredBy(), totalAmount, itemCount);
    }
}
