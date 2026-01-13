package com.elcafe.modules.marketing.event;

import com.elcafe.modules.customer.entity.Customer;
import lombok.Getter;

/**
 * Event fired when a new customer registers.
 * Triggers WELCOME automation.
 */
@Getter
public class CustomerRegisteredEvent extends MarketingEvent {

    private final Customer customer;
    private final boolean isFirstOrder;

    public CustomerRegisteredEvent(Object source, Customer customer) {
        super(source, "CUSTOMER_REGISTERED");
        this.customer = customer;
        this.isFirstOrder = false;
    }

    public CustomerRegisteredEvent(Object source, Customer customer, boolean isFirstOrder) {
        super(source, "CUSTOMER_REGISTERED");
        this.customer = customer;
        this.isFirstOrder = isFirstOrder;
    }

    @Override
    public String getDescription() {
        return String.format("Customer %s %s registered with phone %s",
                customer.getFirstName(), customer.getLastName(), customer.getPhone());
    }
}
