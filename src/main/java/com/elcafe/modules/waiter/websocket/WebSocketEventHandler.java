package com.elcafe.modules.waiter.websocket;

import com.elcafe.modules.waiter.event.*;
import com.elcafe.modules.waiter.websocket.dto.ItemReadyMessage;
import com.elcafe.modules.waiter.websocket.dto.NotificationMessage;
import com.elcafe.modules.waiter.websocket.dto.OrderStatusMessage;
import com.elcafe.modules.waiter.websocket.dto.TableStatusMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Handles conversion of application events to WebSocket messages
 * Listens to waiter events and broadcasts them via WebSocket
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventHandler {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle order created events and broadcast via WebSocket
     */
    @Async
    @EventListener
    public void handleOrderCreatedForWebSocket(OrderCreatedEvent event) {
        log.debug("Broadcasting order created event via WebSocket: {}", event.getOrderNumber());

        try {
            OrderStatusMessage message = OrderStatusMessage.builder()
                    .orderId(event.getOrderId())
                    .orderNumber(event.getOrderNumber())
                    .status("CREATED")
                    .tableId(event.getTableId())
                    .waiterId(event.getWaiterId())
                    .message(String.format("New order created with %d items", event.getItemCount()))
                    .timestamp(event.getEventTimestamp())
                    .build();

            // Broadcast to all waiters
            messagingTemplate.convertAndSend("/topic/waiter/orders", message);

            // Send to specific waiter
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("INFO", "Order created successfully", LocalDateTime.now())
                );
            }
        } catch (Exception e) {
            log.error("Error broadcasting order created event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle order submitted events and broadcast to kitchen
     */
    @Async
    @EventListener
    public void handleOrderSubmittedForWebSocket(OrderSubmittedEvent event) {
        log.debug("Broadcasting order submitted event via WebSocket: {}", event.getOrderNumber());

        try {
            OrderStatusMessage message = OrderStatusMessage.builder()
                    .orderId(event.getOrderId())
                    .orderNumber(event.getOrderNumber())
                    .status("SUBMITTED")
                    .tableId(event.getTableId())
                    .waiterId(event.getWaiterId())
                    .message(String.format("Order submitted to kitchen - Total: $%.2f", event.getTotalAmount()))
                    .timestamp(event.getEventTimestamp())
                    .build();

            // Broadcast to kitchen
            messagingTemplate.convertAndSend("/topic/kitchen", message);

            // Broadcast to all waiters
            messagingTemplate.convertAndSend("/topic/waiter/orders", message);

            // Notify specific waiter
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("SUCCESS", "Order submitted to kitchen", LocalDateTime.now())
                );
            }
        } catch (Exception e) {
            log.error("Error broadcasting order submitted event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle order ready events and notify waiter
     */
    @Async
    @EventListener
    public void handleOrderReadyForWebSocket(OrderReadyEvent event) {
        log.debug("Broadcasting order ready event via WebSocket: {}", event.getOrderNumber());

        try {
            ItemReadyMessage message = ItemReadyMessage.builder()
                    .orderId(event.getOrderId())
                    .orderNumber(event.getOrderNumber())
                    .tableId(event.getTableId())
                    .waiterId(event.getWaiterId())
                    .timestamp(event.getEventTimestamp())
                    .build();

            // Send to specific waiter with high priority
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("WARNING",
                                String.format("Order %s is ready for pickup!", event.getOrderNumber()),
                                LocalDateTime.now())
                );
            }

            // Broadcast to all waiters
            messagingTemplate.convertAndSend("/topic/waiter/orders", message);

            // Broadcast to kitchen (to update display)
            messagingTemplate.convertAndSend("/topic/kitchen", message);
        } catch (Exception e) {
            log.error("Error broadcasting order ready event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle bill requested events
     */
    @Async
    @EventListener
    public void handleBillRequestedForWebSocket(BillRequestedEvent event) {
        log.debug("Broadcasting bill requested event via WebSocket: {}", event.getOrderNumber());

        try {
            OrderStatusMessage message = OrderStatusMessage.builder()
                    .orderId(event.getOrderId())
                    .orderNumber(event.getOrderNumber())
                    .status("BILL_REQUESTED")
                    .tableId(event.getTableId())
                    .waiterId(event.getWaiterId())
                    .message(String.format("Bill requested - Total: $%.2f", event.getTotalAmount()))
                    .timestamp(event.getEventTimestamp())
                    .build();

            // Notify waiter
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("INFO", "Bill requested", LocalDateTime.now())
                );
            }

            // Broadcast to all waiters
            messagingTemplate.convertAndSend("/topic/waiter/orders", message);
        } catch (Exception e) {
            log.error("Error broadcasting bill requested event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle order paid events
     */
    @Async
    @EventListener
    public void handleOrderPaidForWebSocket(OrderPaidEvent event) {
        log.debug("Broadcasting order paid event via WebSocket: {}", event.getOrderNumber());

        try {
            OrderStatusMessage message = OrderStatusMessage.builder()
                    .orderId(event.getOrderId())
                    .orderNumber(event.getOrderNumber())
                    .status("PAID")
                    .tableId(event.getTableId())
                    .waiterId(event.getWaiterId())
                    .message(String.format("Payment completed - $%.2f via %s",
                            event.getAmount(), event.getPaymentMethod()))
                    .timestamp(event.getEventTimestamp())
                    .build();

            // Notify waiter
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("SUCCESS", "Payment completed", LocalDateTime.now())
                );
            }

            // Broadcast to all waiters
            messagingTemplate.convertAndSend("/topic/waiter/orders", message);
        } catch (Exception e) {
            log.error("Error broadcasting order paid event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle item added events
     */
    @Async
    @EventListener
    public void handleItemAddedForWebSocket(OrderItemAddedEvent event) {
        log.debug("Broadcasting item added event via WebSocket: {}", event.getOrderNumber());

        try {
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("INFO",
                                String.format("Item '%s' added to order", event.getItemName()),
                                LocalDateTime.now())
                );
            }
        } catch (Exception e) {
            log.error("Error broadcasting item added event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle item removed events
     */
    @Async
    @EventListener
    public void handleItemRemovedForWebSocket(OrderItemRemovedEvent event) {
        log.debug("Broadcasting item removed event via WebSocket: {}", event.getOrderNumber());

        try {
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("WARNING",
                                String.format("Item '%s' removed - Reason: %s",
                                        event.getItemName(), event.getReason()),
                                LocalDateTime.now())
                );
            }
        } catch (Exception e) {
            log.error("Error broadcasting item removed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle table status changed events and broadcast to all waiters
     */
    @Async
    @EventListener
    public void handleTableStatusChangedForWebSocket(TableStatusChangedEvent event) {
        log.debug("Broadcasting table status changed event via WebSocket: Table {}",
                event.getTableNumber());

        try {
            TableStatusMessage message = TableStatusMessage.builder()
                    .tableId(event.getTableId())
                    .tableNumber(event.getTableNumber())
                    .status(event.getNewStatus().name())
                    .waiterId(event.getWaiterId())
                    .timestamp(event.getEventTimestamp())
                    .build();

            // Broadcast to all waiters
            messagingTemplate.convertAndSend("/topic/table", message);

            // Notify specific waiter if assigned
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("INFO",
                                String.format("Table %d status changed to %s",
                                        event.getTableNumber(), event.getNewStatus()),
                                LocalDateTime.now())
                );
            }
        } catch (Exception e) {
            log.error("Error broadcasting table status changed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Send a custom notification to a specific waiter
     */
    public void sendNotificationToWaiter(Long waiterId, String type, String message) {
        try {
            messagingTemplate.convertAndSendToUser(
                    waiterId.toString(),
                    "/queue/notifications",
                    new NotificationMessage(type, message, LocalDateTime.now())
            );
        } catch (Exception e) {
            log.error("Error sending notification to waiter {}: {}", waiterId, e.getMessage(), e);
        }
    }

    /**
     * Broadcast a message to all waiters
     */
    public void broadcastToAllWaiters(String topic, Object message) {
        try {
            messagingTemplate.convertAndSend("/topic/waiter/" + topic, message);
        } catch (Exception e) {
            log.error("Error broadcasting to all waiters: {}", e.getMessage(), e);
        }
    }

    /**
     * Broadcast a message to kitchen
     */
    public void broadcastToKitchen(Object message) {
        try {
            messagingTemplate.convertAndSend("/topic/kitchen", message);
        } catch (Exception e) {
            log.error("Error broadcasting to kitchen: {}", e.getMessage(), e);
        }
    }
}
