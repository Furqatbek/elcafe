package com.elcafe.modules.marketing.event;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Event fired when an order is completed/delivered.
 * Triggers ORDER_COMPLETED and FIRST_ORDER automations.
 */
@Getter
public class OrderCompletedEvent extends MarketingEvent {

    private final Order order;
    private final Customer customer;
    private final boolean isFirstOrder;
    private final BigDecimal orderTotal;

    public OrderCompletedEvent(Object source, Order order, Customer customer, boolean isFirstOrder) {
        super(source, "ORDER_COMPLETED");
        this.order = order;
        this.customer = customer;
        this.isFirstOrder = isFirstOrder;
        this.orderTotal = order.getTotalAmount();
    }

    @Override
    public String getDescription() {
        return String.format("Order %s completed for customer %s (Total: %s)%s",
                order.getOrderNumber(),
                customer.getFullName(),
                orderTotal,
                isFirstOrder ? " [FIRST ORDER]" : "");
    }
}
