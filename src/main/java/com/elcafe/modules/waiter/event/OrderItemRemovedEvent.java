package com.elcafe.modules.waiter.event;

import com.elcafe.modules.waiter.enums.OrderEventType;
import lombok.Getter;

/**
 * Event fired when an item is removed from an order
 */
@Getter
public class OrderItemRemovedEvent extends WaiterEvent {

    private final String orderNumber;
    private final String itemName;
    private final String reason;

    public OrderItemRemovedEvent(
            Object source,
            Long orderId,
            String orderNumber,
            Long tableId,
            Long waiterId,
            String triggeredBy,
            String itemName,
            String reason) {
        super(source, OrderEventType.ITEM_REMOVED, orderId, tableId, waiterId, triggeredBy);
        this.orderNumber = orderNumber;
        this.itemName = itemName;
        this.reason = reason;

        addMetadata("itemName", itemName);
        addMetadata("reason", reason);
    }

    @Override
    public String getEventDescription() {
        return String.format("Item '%s' removed from order %s by %s - Reason: %s",
                itemName, orderNumber, getTriggeredBy(), reason);
    }
}
