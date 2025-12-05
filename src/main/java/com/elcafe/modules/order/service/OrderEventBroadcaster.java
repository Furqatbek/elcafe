package com.elcafe.modules.order.service;

import com.elcafe.modules.order.dto.OrderEventMessage;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for broadcasting order events via WebSocket.
 * Based on CLIENT_RESTAURANT_FLOW.md documentation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcast order placed event to admin panel.
     * Topic: /topic/restaurant/{restaurantId}/orders
     */
    public void broadcastOrderPlaced(Order order) {
        log.info("Broadcasting order.placed event for order: {}", order.getOrderNumber());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", order.getId());
        eventData.put("orderNumber", order.getOrderNumber());
        eventData.put("consumer", buildConsumerInfo(order));
        eventData.put("orderType", order.getOrderType());
        eventData.put("totalAmount", order.getTotal());
        eventData.put("itemCount", order.getItems().size());
        if (order.getDeliveryInfo() != null) {
            eventData.put("deliveryAddress", buildDeliveryInfo(order));
        }
        eventData.put("placedAt", order.getPlacedAt());

        OrderEventMessage message = OrderEventMessage.builder()
                .eventType("order.placed")
                .timestamp(LocalDateTime.now())
                .data(eventData)
                .build();

        // Send to restaurant-specific topic for admin panel
        String destination = "/topic/restaurant/" + order.getRestaurant().getId() + "/orders";
        messagingTemplate.convertAndSend(destination, message);

        log.info("Order placed event sent to: {}", destination);
    }

    /**
     * Broadcast order accepted event to consumer.
     * Topic: /user/{consumerId}/topic/orders
     */
    public void broadcastOrderAccepted(Order order) {
        log.info("Broadcasting order.accepted event for order: {}", order.getOrderNumber());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", order.getId());
        eventData.put("orderNumber", order.getOrderNumber());
        eventData.put("status", order.getStatus());
        eventData.put("acceptedAt", order.getAcceptedAt());
        // Calculate estimated delivery time (base time + preparation estimate)
        if (order.getAcceptedAt() != null) {
            LocalDateTime estimatedDelivery = order.getAcceptedAt()
                    .plusMinutes(order.getRestaurant().getEstimatedDeliveryTimeMinutes());
            eventData.put("estimatedDeliveryTime", estimatedDelivery);
        }

        OrderEventMessage message = OrderEventMessage.builder()
                .eventType("order.accepted")
                .timestamp(LocalDateTime.now())
                .data(eventData)
                .build();

        // Send to specific consumer
        sendToConsumer(order.getCustomer().getId(), message);
    }

    /**
     * Broadcast order preparing event to consumer.
     */
    public void broadcastOrderPreparing(Order order) {
        log.info("Broadcasting order.preparing event for order: {}", order.getOrderNumber());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", order.getId());
        eventData.put("status", order.getStatus());
        eventData.put("preparingAt", order.getPreparingAt());
        // Estimate ready time (typically 15 minutes)
        if (order.getPreparingAt() != null) {
            eventData.put("estimatedReadyTime", order.getPreparingAt().plusMinutes(15));
        }

        OrderEventMessage message = OrderEventMessage.builder()
                .eventType("order.preparing")
                .timestamp(LocalDateTime.now())
                .data(eventData)
                .build();

        sendToConsumer(order.getCustomer().getId(), message);
    }

    /**
     * Broadcast order ready event to consumer.
     */
    public void broadcastOrderReady(Order order) {
        log.info("Broadcasting order.ready event for order: {}", order.getOrderNumber());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", order.getId());
        eventData.put("status", order.getStatus());
        eventData.put("readyAt", order.getReadyAt());
        eventData.put("message", "Your order is ready for " +
                (order.getOrderType().equals("PICKUP") ? "pickup" : "delivery"));

        OrderEventMessage message = OrderEventMessage.builder()
                .eventType("order.ready")
                .timestamp(LocalDateTime.now())
                .data(eventData)
                .build();

        sendToConsumer(order.getCustomer().getId(), message);
    }

    /**
     * Broadcast order picked up event to consumer.
     */
    public void broadcastOrderPickedUp(Order order) {
        log.info("Broadcasting order.picked_up event for order: {}", order.getOrderNumber());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", order.getId());
        eventData.put("status", order.getStatus());
        eventData.put("pickedUpAt", order.getPickedUpAt());
        if (order.getPickedUpAt() != null) {
            eventData.put("estimatedDeliveryTime", order.getPickedUpAt().plusMinutes(18));
        }

        OrderEventMessage message = OrderEventMessage.builder()
                .eventType("order.picked_up")
                .timestamp(LocalDateTime.now())
                .data(eventData)
                .build();

        sendToConsumer(order.getCustomer().getId(), message);
    }

    /**
     * Broadcast order completed event to consumer.
     */
    public void broadcastOrderCompleted(Order order) {
        log.info("Broadcasting order.completed event for order: {}", order.getOrderNumber());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", order.getId());
        eventData.put("status", order.getStatus());
        eventData.put("completedAt", order.getCompletedAt());
        eventData.put("message", "Thank you for your order!");

        OrderEventMessage message = OrderEventMessage.builder()
                .eventType("order.completed")
                .timestamp(LocalDateTime.now())
                .data(eventData)
                .build();

        sendToConsumer(order.getCustomer().getId(), message);
    }

    /**
     * Broadcast order cancelled event to consumer and admin.
     */
    public void broadcastOrderCancelled(Order order) {
        log.info("Broadcasting order.cancelled event for order: {}", order.getOrderNumber());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", order.getId());
        eventData.put("status", order.getStatus());
        eventData.put("cancelledBy", order.getCancelledBy());
        eventData.put("reason", order.getCancellationReason());
        eventData.put("cancelledAt", order.getCancelledAt());

        if (order.getPayment() != null) {
            Map<String, Object> refundInfo = new HashMap<>();
            refundInfo.put("amount", order.getTotal());
            refundInfo.put("status", "PROCESSING");
            eventData.put("refund", refundInfo);
        }

        OrderEventMessage message = OrderEventMessage.builder()
                .eventType("order.cancelled")
                .timestamp(LocalDateTime.now())
                .data(eventData)
                .build();

        // Send to consumer
        sendToConsumer(order.getCustomer().getId(), message);

        // Also send to admin panel
        String adminDestination = "/topic/restaurant/" + order.getRestaurant().getId() + "/orders";
        messagingTemplate.convertAndSend(adminDestination, message);
    }

    /**
     * Broadcast order rejected event to consumer.
     */
    public void broadcastOrderRejected(Order order, String reason) {
        log.info("Broadcasting order.rejected event for order: {}", order.getOrderNumber());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", order.getId());
        eventData.put("status", order.getStatus());
        eventData.put("reason", reason);
        eventData.put("rejectedAt", order.getRejectedAt());

        if (order.getPayment() != null) {
            Map<String, Object> refundInfo = new HashMap<>();
            refundInfo.put("amount", order.getTotal());
            refundInfo.put("status", "PROCESSING");
            refundInfo.put("estimatedArrival", LocalDateTime.now().plusDays(7));
            eventData.put("refund", refundInfo);
        }

        OrderEventMessage message = OrderEventMessage.builder()
                .eventType("order.rejected")
                .timestamp(LocalDateTime.now())
                .data(eventData)
                .build();

        sendToConsumer(order.getCustomer().getId(), message);
    }

    /**
     * Send message to specific consumer via user-specific destination.
     */
    private void sendToConsumer(Long customerId, OrderEventMessage message) {
        String destination = "/topic/orders";
        messagingTemplate.convertAndSendToUser(
                customerId.toString(),
                destination,
                message
        );
        log.info("Event sent to consumer {} at /user/{}/topic/orders", customerId, customerId);
    }

    /**
     * Build consumer info for order event.
     */
    private Map<String, Object> buildConsumerInfo(Order order) {
        Map<String, Object> consumer = new HashMap<>();
        consumer.put("id", order.getCustomer().getId());
        consumer.put("phoneNumber", order.getCustomer().getPhone());
        consumer.put("firstName", order.getCustomer().getFirstName());
        consumer.put("lastName", order.getCustomer().getLastName());
        return consumer;
    }

    /**
     * Build delivery info for order event.
     */
    private Map<String, Object> buildDeliveryInfo(Order order) {
        Map<String, Object> delivery = new HashMap<>();
        if (order.getDeliveryInfo() != null) {
            delivery.put("address", order.getDeliveryInfo().getAddress());
            delivery.put("city", order.getDeliveryInfo().getCity());
            delivery.put("contactPhone", order.getDeliveryInfo().getContactPhone());
        }
        return delivery;
    }
}
