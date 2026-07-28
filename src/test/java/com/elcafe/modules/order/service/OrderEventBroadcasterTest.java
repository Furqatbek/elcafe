package com.elcafe.modules.order.service;

import com.elcafe.modules.order.dto.OrderEventMessage;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createCustomer;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    @Test @DisplayName("broadcastOrderReady — non-delivery order reads 'pickup'")
    void ready_nonDelivery_saysPickup() {
        // Factory order is DINE_IN. The old getOrderType().equals("PICKUP") compared an enum to a
        // String, so it was always false and every order read "delivery"; assert the corrected copy.
        ArgumentCaptor<OrderEventMessage> captor = ArgumentCaptor.forClass(OrderEventMessage.class);
        broadcaster.broadcastOrderReady(order);
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().getData().get("message")).isEqualTo("Your order is ready for pickup");
    }

    @Test @DisplayName("broadcastOrderReady — delivery order reads 'delivery'")
    void ready_delivery_saysDelivery() {
        order.setOrderType(OrderType.DELIVERY);
        ArgumentCaptor<OrderEventMessage> captor = ArgumentCaptor.forClass(OrderEventMessage.class);
        broadcaster.broadcastOrderReady(order);
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().getData().get("message")).isEqualTo("Your order is ready for delivery");
    }

    @Test @DisplayName("live-tracking broadcasts tolerate a walk-in order with no linked customer")
    void noCustomer_doesNotSendToConsumer_orThrow() {
        // Walk-in / self-service orders have a null customer. Before the guard these NPE'd on
        // order.getCustomer().getId(); now they must simply skip the consumer send.
        order.setCustomer(null);
        broadcaster.broadcastOrderPreparing(order);
        broadcaster.broadcastOrderReady(order);
        broadcaster.broadcastOrderPickedUp(order);
        broadcaster.broadcastOrderCompleted(order);
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }
}
