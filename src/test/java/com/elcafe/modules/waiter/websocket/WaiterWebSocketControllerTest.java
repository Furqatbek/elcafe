package com.elcafe.modules.waiter.websocket;

import com.elcafe.modules.waiter.websocket.dto.OrderStatusMessage;
import com.elcafe.modules.waiter.websocket.dto.TableStatusMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WaiterWebSocketControllerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @InjectMocks private WaiterWebSocketController controller;

    /** A STOMP header accessor whose session is bound to the given tenant (as CONNECT would set). */
    private SimpMessageHeaderAccessor sessionBoundTo(Long restaurantId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        Map<String, Object> attrs = new HashMap<>();
        if (restaurantId != null) {
            attrs.put(StompAuthChannelInterceptor.ATTR_TENANT, restaurantId);
        }
        accessor.setSessionAttributes(attrs);
        return accessor;
    }

    @Test
    @DisplayName("order-status broadcast is scoped to the session's own tenant")
    void orderStatus_scopedToSessionTenant() {
        OrderStatusMessage msg = OrderStatusMessage.builder().orderNumber("A-1").status("READY").build();
        controller.handleOrderStatusUpdate(msg, sessionBoundTo(7L));
        verify(messagingTemplate).convertAndSend(eq("/topic/restaurant/7/kitchen"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/kitchen"), any(Object.class));
    }

    @Test
    @DisplayName("table-status broadcast is scoped to the session's own tenant")
    void tableStatus_scopedToSessionTenant() {
        TableStatusMessage msg = TableStatusMessage.builder().tableNumber(3).status("OCCUPIED").build();
        controller.handleTableStatusUpdate(msg, sessionBoundTo(7L));
        verify(messagingTemplate).convertAndSend(eq("/topic/restaurant/7/table"), any(Object.class));
    }

    @Test
    @DisplayName("an unauthenticated session (no bound tenant) drops the broadcast")
    void noTenant_dropsBroadcast() {
        OrderStatusMessage msg = OrderStatusMessage.builder().orderNumber("A-1").status("READY").build();
        controller.handleOrderStatusUpdate(msg, sessionBoundTo(null));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("broadcastToAllWaiters targets the tenant-scoped topic")
    void broadcast() {
        controller.broadcastToAllWaiters(7L, "orders", "test");
        verify(messagingTemplate).convertAndSend("/topic/restaurant/7/waiter/orders", "test");
    }

    @Test
    @DisplayName("broadcastToKitchen targets the tenant-scoped topic")
    void kitchen() {
        controller.broadcastToKitchen(7L, "test");
        verify(messagingTemplate).convertAndSend("/topic/restaurant/7/kitchen", "test");
    }

    @Test
    @DisplayName("broadcastToKitchen with no restaurantId refuses to send (no cross-tenant leak)")
    void kitchen_noTenant_refuses() {
        controller.broadcastToKitchen(null, "test");
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
