package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventBroadcasterTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @InjectMocks private OrderEventBroadcaster broadcaster;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.NEW);
    }

    @Test
    @DisplayName("broadcastOrderPlaced sends to WebSocket")
    void broadcastOrderPlaced_sends() {
        broadcaster.broadcastOrderPlaced(order);
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("broadcastOrderAccepted sends to WebSocket")
    void broadcastOrderAccepted_sends() {
        order.setStatus(OrderStatus.ACCEPTED);
        broadcaster.broadcastOrderAccepted(order);
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("broadcastOrderReady sends to WebSocket")
    void broadcastOrderReady_sends() {
        order.setStatus(OrderStatus.READY);
        broadcaster.broadcastOrderReady(order);
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("broadcastOrderCancelled sends to WebSocket")
    void broadcastOrderCancelled_sends() {
        order.setStatus(OrderStatus.CANCELLED);
        broadcaster.broadcastOrderCancelled(order);
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }
}
