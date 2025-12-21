package com.elcafe.modules.kitchen.service;

import com.elcafe.modules.kitchen.entity.KitchenOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for broadcasting kitchen order events via WebSocket.
 * Enables real-time updates for kitchen dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenOrderEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcast kitchen order created event.
     * Topic: /topic/restaurant/{restaurantId}/kitchen
     */
    public void broadcastOrderCreated(KitchenOrder kitchenOrder) {
        log.info("Broadcasting kitchen.order.created event for order: {}", kitchenOrder.getOrder().getOrderNumber());

        Map<String, Object> eventData = buildKitchenOrderData(kitchenOrder);
        eventData.put("eventType", "kitchen.order.created");

        sendToKitchen(kitchenOrder.getOrder().getRestaurant().getId(), eventData);
    }

    /**
     * Broadcast kitchen order preparation started event.
     */
    public void broadcastPreparationStarted(KitchenOrder kitchenOrder) {
        log.info("Broadcasting kitchen.order.preparing event for order: {}", kitchenOrder.getOrder().getOrderNumber());

        Map<String, Object> eventData = buildKitchenOrderData(kitchenOrder);
        eventData.put("eventType", "kitchen.order.preparing");

        sendToKitchen(kitchenOrder.getOrder().getRestaurant().getId(), eventData);
    }

    /**
     * Broadcast kitchen order ready event.
     */
    public void broadcastOrderReady(KitchenOrder kitchenOrder) {
        log.info("Broadcasting kitchen.order.ready event for order: {}", kitchenOrder.getOrder().getOrderNumber());

        Map<String, Object> eventData = buildKitchenOrderData(kitchenOrder);
        eventData.put("eventType", "kitchen.order.ready");

        sendToKitchen(kitchenOrder.getOrder().getRestaurant().getId(), eventData);
    }

    /**
     * Broadcast kitchen order picked up event.
     */
    public void broadcastOrderPickedUp(KitchenOrder kitchenOrder) {
        log.info("Broadcasting kitchen.order.picked_up event for order: {}", kitchenOrder.getOrder().getOrderNumber());

        Map<String, Object> eventData = buildKitchenOrderData(kitchenOrder);
        eventData.put("eventType", "kitchen.order.picked_up");

        sendToKitchen(kitchenOrder.getOrder().getRestaurant().getId(), eventData);
    }

    /**
     * Broadcast kitchen order priority updated event.
     */
    public void broadcastPriorityUpdated(KitchenOrder kitchenOrder) {
        log.info("Broadcasting kitchen.order.priority_updated event for order: {}", kitchenOrder.getOrder().getOrderNumber());

        Map<String, Object> eventData = buildKitchenOrderData(kitchenOrder);
        eventData.put("eventType", "kitchen.order.priority_updated");

        sendToKitchen(kitchenOrder.getOrder().getRestaurant().getId(), eventData);
    }

    /**
     * Send message to kitchen topic for specific restaurant.
     */
    private void sendToKitchen(Long restaurantId, Map<String, Object> eventData) {
        String destination = "/topic/restaurant/" + restaurantId + "/kitchen";

        Map<String, Object> message = new HashMap<>();
        message.put("timestamp", LocalDateTime.now());
        message.put("data", eventData);

        messagingTemplate.convertAndSend(destination, message);
        log.info("Kitchen event sent to: {}", destination);
    }

    /**
     * Build kitchen order data for event.
     */
    private Map<String, Object> buildKitchenOrderData(KitchenOrder kitchenOrder) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", kitchenOrder.getId());
        data.put("status", kitchenOrder.getStatus());
        data.put("priority", kitchenOrder.getPriority());
        data.put("orderId", kitchenOrder.getOrder().getId());
        data.put("orderNumber", kitchenOrder.getOrder().getOrderNumber());
        data.put("assignedChef", kitchenOrder.getAssignedChef());
        data.put("estimatedPreparationTimeMinutes", kitchenOrder.getEstimatedPreparationTimeMinutes());
        data.put("actualPreparationTimeMinutes", kitchenOrder.getActualPreparationTimeMinutes());
        data.put("preparationStartedAt", kitchenOrder.getPreparationStartedAt());
        data.put("preparationCompletedAt", kitchenOrder.getPreparationCompletedAt());
        return data;
    }
}
