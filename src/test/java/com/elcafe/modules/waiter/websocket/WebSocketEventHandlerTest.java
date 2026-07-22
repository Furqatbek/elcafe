package com.elcafe.modules.waiter.websocket;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.waiter.event.OrderCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketEventHandlerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private OrderRepository orderRepository;
    @InjectMocks private WebSocketEventHandler handler;

    @Test @DisplayName("handleOrderCreated broadcasts") void created() {
        Order order = createOrder(1L, OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderCreatedEvent event = new OrderCreatedEvent(this, 1L, "W001", 1L, 1L, "Ali", 2);
        handler.handleOrderCreatedForWebSocket(event);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }
}
