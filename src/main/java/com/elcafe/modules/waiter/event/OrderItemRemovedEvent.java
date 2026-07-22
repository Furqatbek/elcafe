package com.elcafe.modules.waiter.event;

import com.elcafe.modules.waiter.enums.OrderEventType;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Event fired when an item is removed from an order
 */
@Getter
public class OrderItemRemovedEvent extends WaiterEvent {

    private final String orderNumber;
    private final String itemName;
    private final String reason;
    private final BigDecimal itemPrice;

    public OrderItemRemovedEvent(
            Object source,
            Long orderId,
            String orderNumber,
            Long tableId,
            Long waiterId,
            String triggeredBy,
            String itemName,
            String reason,
            BigDecimal itemPrice) {
        super(source, OrderEventType.ITEM_REMOVED, orderId, tableId, waiterId, triggeredBy);
        this.orderNumber = orderNumber;
        this.itemName = itemName;
        this.reason = reason;
        this.itemPrice = itemPrice;

        addMetadata("itemName", itemName);
        addMetadata("reason", reason);
        addMetadata("itemPrice", itemPrice != null ? itemPrice.toString() : "0");
    }

    // Backward compatible constructor without price
    public OrderItemRemovedEvent(
            Object source,
            Long orderId,
            String orderNumber,
            Long tableId,
            Long waiterId,
            String triggeredBy,
            String itemName,
            String reason) {
        this(source, orderId, orderNumber, tableId, waiterId, triggeredBy, itemName, reason, BigDecimal.ZERO);
    }

    @Override
    public String getEventDescription() {
        return String.format("Item '%s' removed from order %s by %s - Reason: %s",
                itemName, orderNumber, getTriggeredBy(), reason);
    }
}
