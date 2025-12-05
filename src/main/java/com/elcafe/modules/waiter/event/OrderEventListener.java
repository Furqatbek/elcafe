package com.elcafe.modules.waiter.event;

import com.elcafe.modules.waiter.entity.OrderEvent;
import com.elcafe.modules.waiter.repository.OrderEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listener for all waiter-related events
 * Handles event processing, logging, and integration with other modules
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderEventRepository orderEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Handle order created events
     * - Log the event
     * - Create audit trail
     * - Notify kitchen module (if needed)
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Handling OrderCreatedEvent: {}", event.getEventDescription());

        try {
            createAuditTrail(event);
            log.info("Order {} created successfully with {} items",
                    event.getOrderNumber(), event.getItemCount());
        } catch (Exception e) {
            log.error("Error handling OrderCreatedEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle order submitted events
     * - Log the event
     * - Create audit trail
     * - Notify kitchen module to start preparing
     * - Send notification to kitchen display system
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderSubmitted(OrderSubmittedEvent event) {
        log.info("Handling OrderSubmittedEvent: {}", event.getEventDescription());

        try {
            createAuditTrail(event);

            // TODO: Integrate with KitchenModule
            // kitchenService.notifyNewOrder(event.getOrderId());

            log.info("Order {} submitted to kitchen - Total: ${}, Items: {}",
                    event.getOrderNumber(), event.getTotalAmount(), event.getItemCount());
        } catch (Exception e) {
            log.error("Error handling OrderSubmittedEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle order ready events
     * - Log the event
     * - Create audit trail
     * - Notify waiter via WebSocket
     * - Update order status
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderReady(OrderReadyEvent event) {
        log.info("Handling OrderReadyEvent: {}", event.getEventDescription());

        try {
            createAuditTrail(event);

            // TODO: Send notification to waiter via WebSocket
            // webSocketService.notifyWaiter(event.getWaiterId(), "Order ready for pickup");

            log.info("Order {} is ready for pickup at table {}",
                    event.getOrderNumber(), event.getTableId());
        } catch (Exception e) {
            log.error("Error handling OrderReadyEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle bill requested events
     * - Log the event
     * - Create audit trail
     * - Prepare payment information
     * - Notify payment module
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleBillRequested(BillRequestedEvent event) {
        log.info("Handling BillRequestedEvent: {}", event.getEventDescription());

        try {
            createAuditTrail(event);

            // TODO: Integrate with PaymentModule
            // paymentService.prepareBill(event.getOrderId(), event.getPaymentMethod());

            log.info("Bill requested for order {} - Total: ${}, Method: {}",
                    event.getOrderNumber(), event.getTotalAmount(), event.getPaymentMethod());
        } catch (Exception e) {
            log.error("Error handling BillRequestedEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle order paid events
     * - Log the event
     * - Create audit trail
     * - Update order and payment status
     * - Close table (if all orders paid)
     * - Update waiter performance metrics
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderPaid(OrderPaidEvent event) {
        log.info("Handling OrderPaidEvent: {}", event.getEventDescription());

        try {
            createAuditTrail(event);

            // TODO: Close table if all orders are paid
            // tableService.checkAndCloseTable(event.getTableId());

            // TODO: Update waiter metrics
            // waiterService.updatePerformanceMetrics(event.getWaiterId(), event.getAmount());

            log.info("Payment completed for order {} - Amount: ${}, Transaction: {}",
                    event.getOrderNumber(), event.getAmount(), event.getTransactionId());
        } catch (Exception e) {
            log.error("Error handling OrderPaidEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle item added events
     * - Log the event
     * - Create audit trail
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleItemAdded(OrderItemAddedEvent event) {
        log.info("Handling OrderItemAddedEvent: {}", event.getEventDescription());

        try {
            createAuditTrail(event);
        } catch (Exception e) {
            log.error("Error handling OrderItemAddedEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle item removed events
     * - Log the event
     * - Create audit trail
     * - Track waste/void items
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleItemRemoved(OrderItemRemovedEvent event) {
        log.info("Handling OrderItemRemovedEvent: {}", event.getEventDescription());

        try {
            createAuditTrail(event);

            // TODO: Track void items for reporting
            // reportingService.trackVoidItem(event.getOrderId(), event.getItemName(), event.getReason());

            log.info("Item '{}' removed from order {} - Reason: {}",
                    event.getItemName(), event.getOrderNumber(), event.getReason());
        } catch (Exception e) {
            log.error("Error handling OrderItemRemovedEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle table status changed events
     * - Log the event
     * - Broadcast to all waiters via WebSocket
     * - Update table availability
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTableStatusChanged(TableStatusChangedEvent event) {
        log.info("Handling TableStatusChangedEvent: {}", event.getEventDescription());

        try {
            // TODO: Broadcast to all waiters via WebSocket
            // webSocketService.broadcastTableStatus(event.getTableId(), event.getNewStatus());

            log.info("Table {} status changed from {} to {}",
                    event.getTableNumber(), event.getOldStatus(), event.getNewStatus());
        } catch (Exception e) {
            log.error("Error handling TableStatusChangedEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Create audit trail entry in the database
     */
    private void createAuditTrail(WaiterEvent event) {
        if (event.getOrderId() == null) {
            // Skip audit trail for non-order events (like table status changes)
            return;
        }

        try {
            String metadata = objectMapper.writeValueAsString(event.getMetadata());

            OrderEvent orderEvent = OrderEvent.builder()
                    .order(com.elcafe.modules.order.entity.Order.builder()
                            .id(event.getOrderId())
                            .build())
                    .eventType(event.getEventType())
                    .triggeredBy(event.getTriggeredBy())
                    .metadata(metadata)
                    .build();

            orderEventRepository.save(orderEvent);

            log.debug("Audit trail created for event: {} on order: {}",
                    event.getEventType(), event.getOrderId());
        } catch (JsonProcessingException e) {
            log.error("Error serializing event metadata: {}", e.getMessage());
            // Save without metadata if serialization fails
            OrderEvent orderEvent = OrderEvent.builder()
                    .order(com.elcafe.modules.order.entity.Order.builder()
                            .id(event.getOrderId())
                            .build())
                    .eventType(event.getEventType())
                    .triggeredBy(event.getTriggeredBy())
                    .metadata("{}")
                    .build();

            orderEventRepository.save(orderEvent);
        }
    }
}
