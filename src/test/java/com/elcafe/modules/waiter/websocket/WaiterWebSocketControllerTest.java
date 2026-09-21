package com.elcafe.modules.waiter.websocket;

import com.elcafe.modules.waiter.websocket.dto.OrderStatusMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WaiterWebSocketControllerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @InjectMocks private WaiterWebSocketController controller;

    @Test @DisplayName("broadcastToAllWaiters sends to topic") void broadcast() {
        controller.broadcastToAllWaiters("orders", "test");
        verify(messagingTemplate).convertAndSend("/topic/waiter/orders", "test");
    }
    @Test @DisplayName("broadcastToKitchen sends to topic") void kitchen() {
        controller.broadcastToKitchen("test");
        verify(messagingTemplate).convertAndSend("/topic/kitchen", "test");
    }
}
