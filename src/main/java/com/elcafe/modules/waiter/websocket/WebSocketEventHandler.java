package com.elcafe.modules.waiter.websocket;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Bridges application events to WebSocket messages: listens to waiter/order events and rebroadcasts them.
 *
 * <p><b>Tenant scoping (audit residual):</b> order/kitchen/table broadcasts used to fan out to bare global
 * topics ({@code /topic/kitchen}, {@code /topic/table}, {@code /topic/waiter/*}) shared across every
 * tenant, letting any authenticated session watch all tenants' live orders. Each broadcast is now routed to
 * {@code /topic/restaurant/{restaurantId}/...}, where {@code restaurantId} is resolved server-side from the
 * event's order (or table) — never trusted from a client. The per-user {@code /queue/notifications} sends
 * are already user-scoped and unchanged. Handlers run {@code @Async}; the resolver only reads a lazy
 * {@code @ManyToOne} proxy's id (no extra initialization), matching the existing admin-panel broadcast.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final OrderRepository orderRepository;
    private final RestaurantTableRepository restaurantTableRepository;

    private static String kitchenDest(Long restaurantId) {
        return "/topic/restaurant/" + restaurantId + "/kitchen";
    }

    private static String waiterDest(Long restaurantId, String suffix) {
        return "/topic/restaurant/" + restaurantId + "/waiter/" + suffix;
    }

    private static String tableDest(Long restaurantId) {
        return "/topic/restaurant/" + restaurantId + "/table";
    }

    /** Resolve the owning tenant of an order, or null if the order/restaurant is missing. */
    private Long resolveRestaurantId(Long orderId) {
        if (orderId == null) {
            return null;
        }
        try {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null || order.getRestaurant() == null) {
                return null;
            }
            return order.getRestaurant().getId();
        } catch (Exception e) {
            log.error("Error resolving restaurant for order {}: {}", orderId, e.getMessage(), e);
            return null;
        }
    }

    /** Resolve the owning tenant of a table, or null if the table/restaurant is missing. */
    private Long resolveRestaurantIdFromTable(Long tableId) {
        if (tableId == null) {
            return null;
        }
        try {
            RestaurantTable table = restaurantTableRepository.findById(tableId).orElse(null);
            if (table == null || table.getRestaurant() == null) {
                return null;
            }
            return table.getRestaurant().getId();
        } catch (Exception e) {
            log.error("Error resolving restaurant for table {}: {}", tableId, e.getMessage(), e);
            return null;
        }
    }

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

            // Broadcast to this tenant's waiters
            Long restaurantId = resolveRestaurantId(event.getOrderId());
            if (restaurantId != null) {
                messagingTemplate.convertAndSend(waiterDest(restaurantId, "orders"), message);
            }

            // Send to specific waiter (user-scoped)
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("INFO", "Order created successfully", LocalDateTime.now())
                );
            }

            // Also broadcast to admin panel for real-time dashboard notifications
            broadcastToAdminPanel(event.getOrderId(), "order.placed");

        } catch (Exception e) {
            log.error("Error broadcasting order created event: {}", e.getMessage(), e);
        }
    }

    /**
     * Broadcast order event to admin panel
     * Topic: /topic/restaurant/{restaurantId}/orders
     */
    private void broadcastToAdminPanel(Long orderId, String eventType) {
        try {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null || order.getRestaurant() == null) {
                log.warn("Cannot broadcast to admin panel - order or restaurant not found for orderId: {}", orderId);
                return;
            }

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("orderId", order.getId());
            eventData.put("orderNumber", order.getOrderNumber());
            eventData.put("orderType", order.getOrderType() != null ? order.getOrderType().name() : null);
            eventData.put("totalAmount", order.getTotal());
            eventData.put("itemCount", order.getItems() != null ? order.getItems().size() : 0);
            eventData.put("placedAt", order.getPlacedAt());

            if (order.getDiningTable() != null) {
                eventData.put("tableNumber", order.getDiningTable().getTableNumber());
                eventData.put("tableId", order.getDiningTable().getId());
            }

            if (order.getCustomer() != null) {
                Map<String, Object> consumer = new HashMap<>();
                consumer.put("id", order.getCustomer().getId());
                consumer.put("firstName", order.getCustomer().getFirstName());
                consumer.put("lastName", order.getCustomer().getLastName());
                consumer.put("phoneNumber", order.getCustomer().getPhone());
                eventData.put("consumer", consumer);
            } else {
                Map<String, Object> consumer = new HashMap<>();
                consumer.put("firstName", "Guest");
                eventData.put("consumer", consumer);
            }

            Map<String, Object> message = new HashMap<>();
            message.put("eventType", eventType);
            message.put("timestamp", LocalDateTime.now());
            message.put("data", eventData);

            String destination = "/topic/restaurant/" + order.getRestaurant().getId() + "/orders";
            messagingTemplate.convertAndSend(destination, message);

            log.info("Order event '{}' broadcast to admin panel: {}", eventType, destination);
        } catch (Exception e) {
            log.error("Error broadcasting to admin panel: {}", e.getMessage(), e);
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

            Long restaurantId = resolveRestaurantId(event.getOrderId());
            if (restaurantId != null) {
                // Broadcast to this tenant's kitchen
                messagingTemplate.convertAndSend(kitchenDest(restaurantId), message);
                // Broadcast to this tenant's waiters
                messagingTemplate.convertAndSend(waiterDest(restaurantId, "orders"), message);
            }

            // Broadcast to admin panel
            broadcastToAdminPanel(event.getOrderId(), "order.submitted");

            // Notify specific waiter (user-scoped)
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

            // Send to specific waiter with high priority (user-scoped)
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("WARNING",
                                String.format("Order %s is ready for pickup!", event.getOrderNumber()),
                                LocalDateTime.now())
                );
            }

            Long restaurantId = resolveRestaurantId(event.getOrderId());
            if (restaurantId != null) {
                // Broadcast to this tenant's waiters
                messagingTemplate.convertAndSend(waiterDest(restaurantId, "orders"), message);
                // Broadcast to this tenant's kitchen (to update display)
                messagingTemplate.convertAndSend(kitchenDest(restaurantId), message);
            }

            // Broadcast to admin panel
            broadcastToAdminPanel(event.getOrderId(), "order.ready");
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

            // Notify waiter (user-scoped)
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("INFO", "Bill requested", LocalDateTime.now())
                );
            }

            // Broadcast to this tenant's waiters
            Long restaurantId = resolveRestaurantId(event.getOrderId());
            if (restaurantId != null) {
                messagingTemplate.convertAndSend(waiterDest(restaurantId, "orders"), message);
            }

            // Broadcast to admin panel
            broadcastToAdminPanel(event.getOrderId(), "order.bill_requested");
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

            // Notify waiter (user-scoped)
            if (event.getWaiterId() != null) {
                messagingTemplate.convertAndSendToUser(
                        event.getWaiterId().toString(),
                        "/queue/notifications",
                        new NotificationMessage("SUCCESS", "Payment completed", LocalDateTime.now())
                );
            }

            // Broadcast to this tenant's waiters
            Long restaurantId = resolveRestaurantId(event.getOrderId());
            if (restaurantId != null) {
                messagingTemplate.convertAndSend(waiterDest(restaurantId, "orders"), message);
            }

            // Broadcast to admin panel
            broadcastToAdminPanel(event.getOrderId(), "order.paid");
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

            // Broadcast to admin panel
            broadcastToAdminPanel(event.getOrderId(), "order.item_added");
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

            // Broadcast to admin panel
            broadcastToAdminPanel(event.getOrderId(), "order.item_removed");
        } catch (Exception e) {
            log.error("Error broadcasting item removed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle table status changed events and broadcast to this tenant's waiters
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

            // Broadcast to this tenant's waiters
            Long restaurantId = resolveRestaurantIdFromTable(event.getTableId());
            if (restaurantId != null) {
                messagingTemplate.convertAndSend(tableDest(restaurantId), message);
            }

            // Notify specific waiter if assigned (user-scoped)
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
     * Send a custom notification to a specific waiter (user-scoped, not tenant-broadcast).
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
     * Broadcast a message to a tenant's waiters. {@code restaurantId} identifies the target tenant.
     */
    public void broadcastToAllWaiters(Long restaurantId, String topic, Object message) {
        if (restaurantId == null) {
            log.warn("Refusing to broadcast to waiters with no restaurantId (would leak cross-tenant)");
            return;
        }
        try {
            messagingTemplate.convertAndSend(waiterDest(restaurantId, topic), message);
        } catch (Exception e) {
            log.error("Error broadcasting to waiters: {}", e.getMessage(), e);
        }
    }

    /**
     * Broadcast a message to a tenant's kitchen. {@code restaurantId} identifies the target tenant.
     */
    public void broadcastToKitchen(Long restaurantId, Object message) {
        if (restaurantId == null) {
            log.warn("Refusing to broadcast to kitchen with no restaurantId (would leak cross-tenant)");
            return;
        }
        try {
            messagingTemplate.convertAndSend(kitchenDest(restaurantId), message);
        } catch (Exception e) {
            log.error("Error broadcasting to kitchen: {}", e.getMessage(), e);
        }
    }
}
