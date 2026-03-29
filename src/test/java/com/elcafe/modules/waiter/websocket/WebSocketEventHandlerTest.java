package com.elcafe.modules.waiter.websocket;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.waiter.event.OrderCreatedEvent;
import com.elcafe.modules.waiter.event.OrderSubmittedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketEventHandlerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private OrderRepository orderRepository;
    @InjectMocks private WebSocketEventHandler handler;

    @Test
    @DisplayName("handleOrderCreated broadcasts to waiter/orders topic")
    void handleOrderCreated_broadcasts() {
        Order order = createOrder(1L, OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(1L).orderNumber("W001").waiterId(1L)
                .itemCount(2).totalAmount(BigDecimal.valueOf(50000))
                .eventTimestamp(LocalDateTime.now()).build();

        handler.handleOrderCreatedForWebSocket(event);

        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("handleOrderSubmitted broadcasts to kitchen and waiter topics")
    void handleOrderSubmitted_broadcasts() {
        Order order = createOrder(1L, OrderStatus.PREPARING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderSubmittedEvent event = OrderSubmittedEvent.builder()
                .orderId(1L).orderNumber("W001").waiterId(1L)
                .itemCount(2).totalAmount(BigDecimal.valueOf(50000))
                .eventTimestamp(LocalDateTime.now()).build();

        handler.handleOrderSubmittedForWebSocket(event);

        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }
}
