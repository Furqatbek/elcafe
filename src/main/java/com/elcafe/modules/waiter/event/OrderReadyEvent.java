package com.elcafe.modules.waiter.event;

import com.elcafe.modules.waiter.enums.OrderEventType;
import lombok.Getter;

/**
 * Event fired when the kitchen marks an order as ready for pickup
 */
@Getter
public class OrderReadyEvent extends WaiterEvent {

    private final String orderNumber;
    private final Long kitchenOrderId;

    public OrderReadyEvent(
            Object source,
            Long orderId,
            String orderNumber,
            Long tableId,
            Long waiterId,
            String triggeredBy,
            Long kitchenOrderId) {
        super(source, OrderEventType.ORDER_READY, orderId, tableId, waiterId, triggeredBy);
        this.orderNumber = orderNumber;
        this.kitchenOrderId = kitchenOrderId;
    }

    @Override
    public String getEventDescription() {
        return String.format("Order %s is ready for pickup at table %d - Kitchen Order ID: %d",
                orderNumber, getTableId(), kitchenOrderId);
    }
}
