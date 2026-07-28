package com.elcafe.modules.waiter.event;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.waiter.entity.OrderEvent;
import com.elcafe.modules.waiter.repository.OrderEventRepository;
import com.elcafe.modules.waiter.service.WaiterPerformanceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener for all waiter-related events
 * Handles event processing, logging, and integration with other modules
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderEventRepository orderEventRepository;
    private final OrderRepository orderRepository;
    private final WaiterPerformanceService performanceService;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    /**
     * Handle order created events
     * - Log the event
     * - Create audit trail
     * - Notify kitchen module (if needed)
     *
     * Uses AFTER_COMMIT phase to ensure the order exists in the database
     * before trying to create audit trail records with foreign key references.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderSubmitted(OrderSubmittedEvent event) {
        log.info("Handling OrderSubmittedEvent: {}", event.getEventDescription());

        try {
            createAuditTrail(event);

            // Kitchen integration is no longer done here. The kitchen ticket (the kitchen_orders row
            // the KDS renders) is created synchronously in WaiterOrderService at the submit-to-kitchen
            // transition, so the board shows the order atomically with the submit rather than depending
            // on this async, after-commit listener. This listener stays audit-only.

            log.info("Order {} submitted to kitchen - Total: ${}, Items: {}",
                    event.getOrderNumber(), event.getTotalAmount(), event.getItemCount());
        } catch (Exception e) {
            log.error("Error handling OrderSubmittedEvent: {}", e.getMessage(), e);
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
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderPaid(OrderPaidEvent event) {
        log.info("Handling OrderPaidEvent: {}", event.getEventDescription());

        try {
            createAuditTrail(event);

            // Update waiter performance metrics
            if (event.getWaiterId() != null && event.getOrderId() != null) {
                try {
                    Order order = orderRepository.findById(event.getOrderId()).orElse(null);
                    if (order != null && order.getRestaurant() != null) {
                        performanceService.recordOrderCompletion(order, event.getWaiterId(), order.getRestaurant().getId());
                        log.info("Updated performance metrics for waiter {}", event.getWaiterId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to update waiter performance: {}", e.getMessage());
                }
            }

            log.info("Payment completed for order {} - Amount: ${}, Transaction: {}",
                    event.getOrderNumber(), event.getAmount(), event.getTransactionId());
        } catch (Exception e) {
            log.error("Error handling OrderPaidEvent: {}", e.getMessage(), e);
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
                    .order(entityManager.getReference(Order.class, event.getOrderId()))
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
                    .order(entityManager.getReference(Order.class, event.getOrderId()))
                    .eventType(event.getEventType())
                    .triggeredBy(event.getTriggeredBy())
                    .metadata("{}")
                    .build();

            orderEventRepository.save(orderEvent);
        }
    }
}
