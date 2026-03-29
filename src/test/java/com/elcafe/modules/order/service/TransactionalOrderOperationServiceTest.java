package com.elcafe.modules.order.service;

import com.elcafe.modules.financial.service.RevenueService;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.kitchen.service.KitchenOrderService;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionalOrderOperationServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private KitchenOrderService kitchenOrderService;
    @Mock private InventoryService inventoryService;
    @Mock private NotificationService notificationService;
    @Mock private RevenueService revenueService;

    @InjectMocks private TransactionalOrderOperationService service;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.NEW);
    }

    @Test
    @DisplayName("acceptOrderAndCreateKitchenOrder — changes status to ACCEPTED")
    void acceptOrder_changesStatus() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(inventoryService.checkIngredientAvailability(any())).thenReturn(true);

        Order result = service.acceptOrderAndCreateKitchenOrder(1L, "admin");

        assertEquals(OrderStatus.ACCEPTED, result.getStatus());
    }

    @Test
    @DisplayName("acceptOrder — not found throws")
    void acceptOrder_notFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(Exception.class,
                () -> service.acceptOrderAndCreateKitchenOrder(99L, "admin"));
    }

    @Test
    @DisplayName("voidOrderWithPayments — cancels order")
    void voidOrder_cancelsOrder() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(1L)).thenReturn(java.util.List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = service.voidOrderWithPayments(1L, "Wrong order", "admin");

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
    }
}
