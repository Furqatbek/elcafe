package com.elcafe.modules.waiter.event;

import com.elcafe.modules.waiter.enums.OrderEventType;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Event fired when an item is added to an order
 */
@Getter
public class OrderItemAddedEvent extends WaiterEvent {

    private final String orderNumber;
    private final String itemName;
    private final Integer quantity;
    private final BigDecimal price;

    public OrderItemAddedEvent(
            Object source,
            Long orderId,
            String orderNumber,
            Long tableId,
            Long waiterId,
            String triggeredBy,
            String itemName,
            Integer quantity,
            BigDecimal price) {
        super(source, OrderEventType.ITEM_ADDED, orderId, tableId, waiterId, triggeredBy);
        this.orderNumber = orderNumber;
        this.itemName = itemName;
        this.quantity = quantity;
        this.price = price;

        addMetadata("itemName", itemName);
        addMetadata("quantity", quantity);
        addMetadata("price", price);
    }

    @Override
    public String getEventDescription() {
        return String.format("Item '%s' (qty: %d) added to order %s by %s",
                itemName, quantity, orderNumber, getTriggeredBy());
    }
}
