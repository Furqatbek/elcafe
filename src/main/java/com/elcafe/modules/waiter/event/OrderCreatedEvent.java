package com.elcafe.modules.waiter.event;

import com.elcafe.modules.waiter.enums.OrderEventType;
import lombok.Getter;

/**
 * Event fired when a new order is created by a waiter
 */
@Getter
public class OrderCreatedEvent extends WaiterEvent {

    private final String orderNumber;
    private final Integer itemCount;

    public OrderCreatedEvent(
            Object source,
            Long orderId,
            String orderNumber,
            Long tableId,
            Long waiterId,
            String triggeredBy,
            Integer itemCount) {
        super(source, OrderEventType.ORDER_CREATED, orderId, tableId, waiterId, triggeredBy);
        this.orderNumber = orderNumber;
        this.itemCount = itemCount;
    }

    @Override
    public String getEventDescription() {
        return String.format("Order %s created by %s with %d items for table %d",
                orderNumber, getTriggeredBy(), itemCount, getTableId());
    }
}
