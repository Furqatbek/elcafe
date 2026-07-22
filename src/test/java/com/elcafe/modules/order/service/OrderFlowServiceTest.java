package com.elcafe.modules.order.service;

import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.service.KitchenOrderService;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFlowServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private KitchenOrderService kitchenOrderService;
    @Mock private NotificationService notificationService;

    @InjectMocks private OrderFlowService orderFlowService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.NEW);
    }

    @Test
    @DisplayName("Accept order — NEW → ACCEPTED, creates kitchen order")
    void acceptOrder_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(kitchenOrderService.createKitchenOrder(any(Order.class)))
                .thenReturn(KitchenOrder.builder().id(1L).build());

        Order result = orderFlowService.acceptOrder(1L, "admin");

        assertEquals(OrderStatus.ACCEPTED, result.getStatus());
        verify(kitchenOrderService).createKitchenOrder(any(Order.class));
        verify(notificationService).notifyOrderAccepted(any(Order.class));
    }

    @Test
    @DisplayName("Accept order — adds status history")
    void acceptOrder_addsStatusHistory() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(kitchenOrderService.createKitchenOrder(any(Order.class)))
                .thenReturn(KitchenOrder.builder().id(1L).build());

        Order result = orderFlowService.acceptOrder(1L, "admin");

        assertNotNull(result.getStatusHistory());
        assertEquals(1, result.getStatusHistory().size());
    }

    @Test
    @DisplayName("Accept non-NEW order — throws")
    void acceptOrder_notNew_throws() {
        order.setStatus(OrderStatus.PREPARING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(RuntimeException.class,
                () -> orderFlowService.acceptOrder(1L, "admin"));
    }

    @Test
    @DisplayName("Accept order not found — throws")
    void acceptOrder_notFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> orderFlowService.acceptOrder(99L, "admin"));
    }

    @Test
    @DisplayName("getOrderFlowDocumentation — returns non-empty string")
    void getOrderFlowDocumentation_returnsNonEmpty() {
        String docs = orderFlowService.getOrderFlowDocumentation();

        assertNotNull(docs);
        assertTrue(docs.contains("ORDER FLOW"), "Should contain flow documentation");
    }

    private void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }
}
