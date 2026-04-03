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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createCustomer;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderEventBroadcasterTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @InjectMocks private OrderEventBroadcaster broadcaster;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.NEW);
        order.setCustomer(createCustomer());
    }

    @Test @DisplayName("broadcastOrderPlaced") void placed() {
        broadcaster.broadcastOrderPlaced(order);
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }
    @Test @DisplayName("broadcastOrderAccepted") void accepted() {
        broadcaster.broadcastOrderAccepted(order);
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }
}
