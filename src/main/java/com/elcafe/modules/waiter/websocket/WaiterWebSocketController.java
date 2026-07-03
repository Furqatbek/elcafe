package com.elcafe.modules.waiter.websocket;

import com.elcafe.modules.waiter.websocket.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebSocket controller for real-time waiter operations. Processes incoming SEND frames and rebroadcasts
 * them to subscribed clients.
 *
 * <p><b>Tenant scoping (audit residual):</b> these handlers used to fan out to bare global topics
 * ({@code /topic/kitchen}, {@code /topic/table}, {@code /topic/waiter/*}) shared across every tenant, so
 * any authenticated session could inject fabricated events into all tenants' streams. They now publish to
 * {@code /topic/restaurant/{restaurantId}/...}, where {@code restaurantId} is the tenant bound to THIS
 * STOMP session at CONNECT ({@link StompAuthChannelInterceptor#ATTR_TENANT}) — never a client-supplied
 * value — so a session can only ever address its own tenant's stream, and subscribers are tenant-checked
 * by the interceptor. A session with no bound tenant (websocket auth off / shadow with no token) has no
 * addressable stream, so the message is dropped.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WaiterWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle order status updates from kitchen. Broadcast to this tenant's waiters/kitchen display.
     */
    @MessageMapping("/kitchen/order-status")
    public void handleOrderStatusUpdate(@Payload OrderStatusMessage message,
                                        SimpMessageHeaderAccessor headerAccessor) {
        log.info("Received order status update: Order {} is now {}",
                message.getOrderNumber(), message.getStatus());
        message.setTimestamp(LocalDateTime.now());
        sendToTenant(headerAccessor, "kitchen", message);
    }

    /**
     * Handle table status updates. Broadcast table status changes to this tenant's waiters.
     */
    @MessageMapping("/table/status")
    public void handleTableStatusUpdate(@Payload TableStatusMessage message,
                                        SimpMessageHeaderAccessor headerAccessor) {
        log.info("Received table status update: Table {} is now {}",
                message.getTableNumber(), message.getStatus());
        message.setTimestamp(LocalDateTime.now());
        sendToTenant(headerAccessor, "table", message);
    }

    /**
     * Handle waiter requests (e.g., calling for help, requesting manager).
     * Broadcast to this tenant's supervisors and managers.
     */
    @MessageMapping("/waiter/request")
    public void handleWaiterRequest(@Payload WaiterRequestMessage message,
                                    SimpMessageHeaderAccessor headerAccessor) {
        log.info("Received waiter request: {} from waiter {} for table {}",
                message.getRequestType(), message.getWaiterName(), message.getTableNumber());
        message.setTimestamp(LocalDateTime.now());
        sendToTenant(headerAccessor, "waiter/requests", message);
    }

    /**
     * Handle item ready notifications from kitchen. Notify the assigned waiter and this tenant's kitchen.
     */
    @MessageMapping("/kitchen/item-ready")
    public void handleItemReady(@Payload ItemReadyMessage message,
                                SimpMessageHeaderAccessor headerAccessor) {
        log.info("Item ready notification: Order {}, Item {}",
                message.getOrderNumber(), message.getItemName());

        message.setTimestamp(LocalDateTime.now());

        // Send to specific waiter (user-scoped queue — not tenant-broadcast)
        if (message.getWaiterId() != null) {
            messagingTemplate.convertAndSendToUser(
                    message.getWaiterId().toString(),
                    "/queue/notifications",
                    message
            );
        }

        // Also broadcast to this tenant's kitchen topic
        sendToTenant(headerAccessor, "kitchen", message);
    }

    /**
     * Handle customer call button presses. Notify the assigned waiter and this tenant's waiters.
     */
    @MessageMapping("/table/call-waiter")
    public void handleCallWaiter(@Payload CallWaiterMessage message,
                                 SimpMessageHeaderAccessor headerAccessor) {
        log.info("Call waiter request from table {}", message.getTableNumber());

        message.setTimestamp(LocalDateTime.now());

        // Send to specific waiter if assigned (user-scoped queue)
        if (message.getWaiterId() != null) {
            messagingTemplate.convertAndSendToUser(
                    message.getWaiterId().toString(),
                    "/queue/notifications",
                    message
            );
        }

        // Broadcast to this tenant's waiters
        sendToTenant(headerAccessor, "waiter/calls", message);
    }

    /**
     * Handle waiter connection events. Track which waiters are currently online.
     */
    @MessageMapping("/waiter/connect")
    public void handleWaiterConnect(@Payload WaiterConnectMessage message,
                                     SimpMessageHeaderAccessor headerAccessor) {
        log.info("Waiter {} connected", message.getWaiterName());

        // Store waiter info in session attributes
        headerAccessor.getSessionAttributes().put("waiterId", message.getWaiterId());
        headerAccessor.getSessionAttributes().put("waiterName", message.getWaiterName());

        // Broadcast waiter online status to this tenant
        WaiterStatusMessage statusMessage = new WaiterStatusMessage(
                message.getWaiterId(),
                message.getWaiterName(),
                "ONLINE",
                LocalDateTime.now()
        );

        sendToTenant(headerAccessor, "waiter/status", statusMessage);
    }

    /**
     * Handle waiter disconnection events.
     */
    @MessageMapping("/waiter/disconnect")
    public void handleWaiterDisconnect(@Payload WaiterConnectMessage message,
                                       SimpMessageHeaderAccessor headerAccessor) {
        log.info("Waiter {} disconnected", message.getWaiterName());

        // Broadcast waiter offline status to this tenant
        WaiterStatusMessage statusMessage = new WaiterStatusMessage(
                message.getWaiterId(),
                message.getWaiterName(),
                "OFFLINE",
                LocalDateTime.now()
        );

        sendToTenant(headerAccessor, "waiter/status", statusMessage);
    }

    /**
     * Send a notification to a specific waiter (user-scoped, not tenant-broadcast).
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
     * Broadcast a message to a tenant's waiters. {@code restaurantId} identifies the target tenant.
     */
    public void broadcastToAllWaiters(Long restaurantId, String topic, Object message) {
        if (restaurantId == null) {
            log.warn("Refusing to broadcast to waiters with no restaurantId (would leak cross-tenant)");
            return;
        }
        messagingTemplate.convertAndSend("/topic/restaurant/" + restaurantId + "/waiter/" + topic, message);
    }

    /**
     * Broadcast a message to a tenant's kitchen staff. {@code restaurantId} identifies the target tenant.
     */
    public void broadcastToKitchen(Long restaurantId, Object message) {
        if (restaurantId == null) {
            log.warn("Refusing to broadcast to kitchen with no restaurantId (would leak cross-tenant)");
            return;
        }
        messagingTemplate.convertAndSend("/topic/restaurant/" + restaurantId + "/kitchen", message);
    }

    /**
     * Publish {@code payload} to {@code /topic/restaurant/{tenant}/{suffix}} for the tenant bound to this
     * STOMP session at CONNECT. The tenant is read from the session, never from the client payload, so a
     * session can only address its own tenant's stream. Dropped (with a log) if the session is unauthenticated.
     */
    private void sendToTenant(SimpMessageHeaderAccessor headerAccessor, String suffix, Object payload) {
        Long restaurantId = sessionTenantId(headerAccessor);
        if (restaurantId == null) {
            log.warn("Dropping WS message to '{}': session has no bound tenant (unauthenticated)", suffix);
            return;
        }
        messagingTemplate.convertAndSend("/topic/restaurant/" + restaurantId + "/" + suffix, payload);
    }

    /** The tenant bound to this STOMP session at CONNECT, or null if unauthenticated. */
    private Long sessionTenantId(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        Object bound = attrs == null ? null : attrs.get(StompAuthChannelInterceptor.ATTR_TENANT);
        return bound instanceof Long id ? id : null;
    }
}
