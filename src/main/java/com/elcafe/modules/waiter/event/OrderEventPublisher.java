package com.elcafe.modules.waiter.event;

import com.elcafe.modules.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Publisher for all waiter-related events
 * Uses Spring's ApplicationEventPublisher for async event handling
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Publish event when a new order is created
     * Note: Not async - event must be published within the caller's transaction
     * so that @TransactionalEventListener(phase = AFTER_COMMIT) works correctly
     */
    public void publishOrderCreated(Order order, String triggeredBy) {
        log.info("Publishing order created event for order: {}", order.getOrderNumber());

        OrderCreatedEvent event = new OrderCreatedEvent(
                this,
                order.getId(),
                order.getOrderNumber(),
                order.getDiningTable() != null ? order.getDiningTable().getId() : null,
                order.getWaiter() != null ? order.getWaiter().getId() : null,
                triggeredBy,
                order.getItems() != null ? order.getItems().size() : 0
        );

        eventPublisher.publishEvent(event);
    }

    /**
     * Publish event when an order is submitted to the kitchen
     */
    public void publishOrderSubmitted(Order order, String triggeredBy) {
        log.info("Publishing order submitted event for order: {}", order.getOrderNumber());

        OrderSubmittedEvent event = new OrderSubmittedEvent(
                this,
                order.getId(),
                order.getOrderNumber(),
                order.getDiningTable() != null ? order.getDiningTable().getId() : null,
                order.getWaiter() != null ? order.getWaiter().getId() : null,
                triggeredBy,
                order.getTotal(),
                order.getItems() != null ? order.getItems().size() : 0
        );

        eventPublisher.publishEvent(event);
    }

    /**
     * Publish event when bill is requested
     */
    public void publishBillRequested(Order order, String paymentMethod, String triggeredBy) {
        log.info("Publishing bill requested event for order: {}", order.getOrderNumber());

        BillRequestedEvent event = new BillRequestedEvent(
                this,
                order.getId(),
                order.getOrderNumber(),
                order.getDiningTable() != null ? order.getDiningTable().getId() : null,
                order.getWaiter() != null ? order.getWaiter().getId() : null,
                triggeredBy,
                order.getTotal(),
                paymentMethod
        );

        eventPublisher.publishEvent(event);
    }

    /**
     * Publish event when payment is completed
     */
    public void publishOrderPaid(
            Order order,
            BigDecimal amount,
            String paymentMethod,
            String transactionId,
            String triggeredBy) {
        log.info("Publishing order paid event for order: {}", order.getOrderNumber());

        OrderPaidEvent event = new OrderPaidEvent(
                this,
                order.getId(),
                order.getOrderNumber(),
                order.getDiningTable() != null ? order.getDiningTable().getId() : null,
                order.getWaiter() != null ? order.getWaiter().getId() : null,
                triggeredBy,
                amount,
                paymentMethod,
                transactionId
        );

        eventPublisher.publishEvent(event);
    }
}
