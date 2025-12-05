package com.elcafe.modules.waiter.event;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.waiter.entity.Table;
import com.elcafe.modules.waiter.enums.TableStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
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
     */
    @Async
    public void publishOrderCreated(Order order, String triggeredBy) {
        log.info("Publishing order created event for order: {}", order.getOrderNumber());

        OrderCreatedEvent event = new OrderCreatedEvent(
                this,
                order.getId(),
                order.getOrderNumber(),
                order.getTable() != null ? order.getTable().getId() : null,
                order.getWaiter() != null ? order.getWaiter().getId() : null,
                triggeredBy,
                order.getItems() != null ? order.getItems().size() : 0
        );

        eventPublisher.publishEvent(event);
    }

    /**
     * Publish event when an order is submitted to the kitchen
     */
    @Async
    public void publishOrderSubmitted(Order order, String triggeredBy) {
        log.info("Publishing order submitted event for order: {}", order.getOrderNumber());

        OrderSubmittedEvent event = new OrderSubmittedEvent(
                this,
                order.getId(),
                order.getOrderNumber(),
                order.getTable() != null ? order.getTable().getId() : null,
                order.getWaiter() != null ? order.getWaiter().getId() : null,
                triggeredBy,
                order.getTotal(),
                order.getItems() != null ? order.getItems().size() : 0
        );

        eventPublisher.publishEvent(event);
    }

    /**
     * Publish event when an order is ready for pickup
     */
    @Async
    public void publishOrderReady(Order order, Long kitchenOrderId, String triggeredBy) {
        log.info("Publishing order ready event for order: {}", order.getOrderNumber());

        OrderReadyEvent event = new OrderReadyEvent(
                this,
                order.getId(),
                order.getOrderNumber(),
                order.getTable() != null ? order.getTable().getId() : null,
                order.getWaiter() != null ? order.getWaiter().getId() : null,
                triggeredBy,
                kitchenOrderId
        );

        eventPublisher.publishEvent(event);
    }

    /**
     * Publish event when bill is requested
     */
    @Async
    public void publishBillRequested(Order order, String paymentMethod, String triggeredBy) {
        log.info("Publishing bill requested event for order: {}", order.getOrderNumber());

        BillRequestedEvent event = new BillRequestedEvent(
                this,
                order.getId(),
                order.getOrderNumber(),
                order.getTable() != null ? order.getTable().getId() : null,
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
    @Async
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
                order.getTable() != null ? order.getTable().getId() : null,
                order.getWaiter() != null ? order.getWaiter().getId() : null,
                triggeredBy,
                amount,
                paymentMethod,
                transactionId
        );

        eventPublisher.publishEvent(event);
    }

    /**
     * Publish event when an item is added to an order
     */
    @Async
    public void publishItemAdded(
            Order order,
            String itemName,
            Integer quantity,
            BigDecimal price,
            String triggeredBy) {
        log.info("Publishing item added event for order: {}", order.getOrderNumber());

        OrderItemAddedEvent event = new OrderItemAddedEvent(
                this,
                order.getId(),
                order.getOrderNumber(),
                order.getTable() != null ? order.getTable().getId() : null,
                order.getWaiter() != null ? order.getWaiter().getId() : null,
                triggeredBy,
                itemName,
                quantity,
                price
        );

        eventPublisher.publishEvent(event);
    }

    /**
     * Publish event when an item is removed from an order
     */
    @Async
    public void publishItemRemoved(
            Order order,
            String itemName,
            String reason,
            String triggeredBy) {
        log.info("Publishing item removed event for order: {}", order.getOrderNumber());

        OrderItemRemovedEvent event = new OrderItemRemovedEvent(
                this,
                order.getId(),
                order.getOrderNumber(),
                order.getTable() != null ? order.getTable().getId() : null,
                order.getWaiter() != null ? order.getWaiter().getId() : null,
                triggeredBy,
                itemName,
                reason
        );

        eventPublisher.publishEvent(event);
    }

    /**
     * Publish event when table status changes
     */
    @Async
    public void publishTableStatusChanged(
            Table table,
            TableStatus oldStatus,
            TableStatus newStatus,
            String triggeredBy) {
        log.info("Publishing table status changed event for table: {}", table.getNumber());

        TableStatusChangedEvent event = new TableStatusChangedEvent(
                this,
                table.getId(),
                table.getNumber(),
                table.getCurrentWaiter() != null ? table.getCurrentWaiter().getId() : null,
                triggeredBy,
                oldStatus,
                newStatus
        );

        eventPublisher.publishEvent(event);
    }
}
