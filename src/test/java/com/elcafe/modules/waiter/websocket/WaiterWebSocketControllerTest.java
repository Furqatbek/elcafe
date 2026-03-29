package com.elcafe.modules.waiter.websocket;

import com.elcafe.modules.waiter.websocket.dto.OrderStatusMessage;
import com.elcafe.modules.waiter.websocket.dto.CallWaiterMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WaiterWebSocketControllerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @InjectMocks private WaiterWebSocketController controller;

    @Test
    @DisplayName("handleOrderStatusUpdate broadcasts to kitchen")
    void handleOrderStatusUpdate_broadcasts() {
        OrderStatusMessage msg = OrderStatusMessage.builder()
                .orderId(1L).status("READY").build();
        controller.handleOrderStatusUpdate(msg);
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("handleCallWaiter broadcasts call")
    void handleCallWaiter_broadcasts() {
        CallWaiterMessage msg = CallWaiterMessage.builder()
                .tableId(1L).tableNumber("T1").build();
        controller.handleCallWaiter(msg);
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("broadcastToAllWaiters sends to topic")
    void broadcastToAllWaiters_sends() {
        controller.broadcastToAllWaiters("orders", "test");
        verify(messagingTemplate).convertAndSend("/topic/waiter/orders", "test");
    }

    @Test
    @DisplayName("broadcastToKitchen sends to topic")
    void broadcastToKitchen_sends() {
        controller.broadcastToKitchen("test");
        verify(messagingTemplate).convertAndSend("/topic/kitchen", "test");
    }
}
