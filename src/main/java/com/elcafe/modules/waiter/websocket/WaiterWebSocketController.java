package com.elcafe.modules.waiter.websocket;

import com.elcafe.modules.waiter.websocket.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

/**
 * WebSocket controller for handling real-time waiter operations
 * Processes incoming messages and broadcasts updates to subscribed clients
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WaiterWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle order status updates from kitchen
     * Kitchen staff sends updates that are broadcast to all waiters
     */
    @MessageMapping("/kitchen/order-status")
    @SendTo("/topic/kitchen")
    public OrderStatusMessage handleOrderStatusUpdate(@Payload OrderStatusMessage message) {
        log.info("Received order status update: Order {} is now {}",
                message.getOrderNumber(), message.getStatus());

        message.setTimestamp(LocalDateTime.now());
        return message;
    }

    /**
     * Handle table status updates
     * Broadcast table status changes to all waiters
     */
    @MessageMapping("/table/status")
    @SendTo("/topic/table")
    public TableStatusMessage handleTableStatusUpdate(@Payload TableStatusMessage message) {
        log.info("Received table status update: Table {} is now {}",
                message.getTableNumber(), message.getStatus());

        message.setTimestamp(LocalDateTime.now());
        return message;
    }

    /**
     * Handle waiter requests (e.g., calling for help, requesting manager)
     * Broadcast to supervisors and managers
     */
    @MessageMapping("/waiter/request")
    @SendTo("/topic/waiter/requests")
    public WaiterRequestMessage handleWaiterRequest(@Payload WaiterRequestMessage message) {
        log.info("Received waiter request: {} from waiter {} for table {}",
                message.getRequestType(), message.getWaiterName(), message.getTableNumber());

        message.setTimestamp(LocalDateTime.now());
        return message;
    }

    /**
     * Handle item ready notifications from kitchen
     * Notify specific waiter assigned to the order
     */
    @MessageMapping("/kitchen/item-ready")
    public void handleItemReady(@Payload ItemReadyMessage message) {
        log.info("Item ready notification: Order {}, Item {}",
                message.getOrderNumber(), message.getItemName());

        message.setTimestamp(LocalDateTime.now());

        // Send to specific waiter
        if (message.getWaiterId() != null) {
            messagingTemplate.convertAndSendToUser(
                    message.getWaiterId().toString(),
                    "/queue/notifications",
                    message
            );
        }

        // Also broadcast to kitchen topic
        messagingTemplate.convertAndSend("/topic/kitchen", message);
    }

    /**
     * Handle customer call button presses
     * Notify assigned waiter and broadcast to all waiters
     */
    @MessageMapping("/table/call-waiter")
    public void handleCallWaiter(@Payload CallWaiterMessage message) {
        log.info("Call waiter request from table {}", message.getTableNumber());

        message.setTimestamp(LocalDateTime.now());

        // Send to specific waiter if assigned
        if (message.getWaiterId() != null) {
            messagingTemplate.convertAndSendToUser(
                    message.getWaiterId().toString(),
                    "/queue/notifications",
                    message
            );
        }

        // Broadcast to all waiters
        messagingTemplate.convertAndSend("/topic/waiter/calls", message);
    }

    /**
     * Handle waiter connection events
     * Track which waiters are currently online
     */
    @MessageMapping("/waiter/connect")
    public void handleWaiterConnect(@Payload WaiterConnectMessage message,
                                     SimpMessageHeaderAccessor headerAccessor) {
        log.info("Waiter {} connected", message.getWaiterName());

        // Store waiter info in session attributes
        headerAccessor.getSessionAttributes().put("waiterId", message.getWaiterId());
        headerAccessor.getSessionAttributes().put("waiterName", message.getWaiterName());

        // Broadcast waiter online status
        WaiterStatusMessage statusMessage = new WaiterStatusMessage(
                message.getWaiterId(),
                message.getWaiterName(),
                "ONLINE",
                LocalDateTime.now()
        );

        messagingTemplate.convertAndSend("/topic/waiter/status", statusMessage);
    }

    /**
     * Handle waiter disconnection events
     */
    @MessageMapping("/waiter/disconnect")
    public void handleWaiterDisconnect(@Payload WaiterConnectMessage message) {
        log.info("Waiter {} disconnected", message.getWaiterName());

        // Broadcast waiter offline status
        WaiterStatusMessage statusMessage = new WaiterStatusMessage(
                message.getWaiterId(),
                message.getWaiterName(),
                "OFFLINE",
                LocalDateTime.now()
        );

        messagingTemplate.convertAndSend("/topic/waiter/status", statusMessage);
    }

    /**
     * Send notification to a specific waiter
     */
    public void notifyWaiter(Long waiterId, String message) {
        NotificationMessage notification = new NotificationMessage(
                "INFO",
                message,
                LocalDateTime.now()
        );

        messagingTemplate.convertAndSendToUser(
                waiterId.toString(),
                "/queue/notifications",
                notification
        );
    }

    /**
     * Broadcast message to all waiters
     */
    public void broadcastToAllWaiters(String topic, Object message) {
        messagingTemplate.convertAndSend("/topic/waiter/" + topic, message);
    }

    /**
     * Broadcast message to all kitchen staff
     */
    public void broadcastToKitchen(Object message) {
        messagingTemplate.convertAndSend("/topic/kitchen", message);
    }
}
