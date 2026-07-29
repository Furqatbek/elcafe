package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.financial.service.RevenueService;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.inventory.service.InventoryValuationService;
import com.elcafe.modules.kitchen.service.KitchenOrderService;
import com.elcafe.modules.notification.service.CustomerNotificationService;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import com.elcafe.modules.settings.service.PrintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrderItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private InventoryService inventoryService;
    @Mock private DailyOrderSequenceService dailyOrderSequenceService;
    @Mock private RestaurantTableRepository restaurantTableRepository;
    @Mock private ShiftTimeService shiftTimeService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RevenueService revenueService;
    @Mock private PrintService printService;
    @Mock private InventoryValuationService inventoryValuationService;
    @Mock private OwnerNotificationService ownerNotificationService;
    @Mock private CustomerNotificationService customerNotificationService;
    @Mock private OrderEventBroadcaster orderEventBroadcaster;
    @Mock private KitchenOrderService kitchenOrderService;

    @InjectMocks private OrderService orderService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.NEW);
        order.getItems().add(createOrderItem(1L, 1L, "Steak", 1, BigDecimal.valueOf(80000)));
    }

    // ==================== getOrderById ====================

    @Test
    @DisplayName("getOrderById — found")
    void getOrderById_found() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Order result = orderService.getOrderById(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("getOrderById — not found throws")
    void getOrderById_notFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(99L));
    }

    // ==================== getOrderByNumber ====================

    @Test
    @DisplayName("getOrderByNumber — found")
    void getOrderByNumber_found() {
        when(orderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(order));

        Order result = orderService.getOrderByNumber("ORD-001");

        assertNotNull(result);
    }

    @Test
    @DisplayName("getOrderByNumber — not found throws")
    void getOrderByNumber_notFound_throws() {
        when(orderRepository.findByOrderNumber("NONE")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderByNumber("NONE"));
    }

    // ==================== getAllOrders ====================

    @Test
    @DisplayName("getAllOrders — returns page")
    @SuppressWarnings("unchecked")
    void getAllOrders_returnsPage() {
        Page<Order> page = new PageImpl<>(List.of(order));
        org.mockito.Mockito.doReturn(page).when(orderRepository)
                .findAll((Specification<Order>) any(), any(org.springframework.data.domain.Pageable.class));

        Page<Order> result = orderService.getAllOrders(PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    // ==================== getOrdersWithFilters ====================

    @Test
    @DisplayName("getOrdersWithFilters — delegates to spec query")
    @SuppressWarnings("unchecked")
    void getOrdersWithFilters_delegatesToSpec() {
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findAll((Specification<Order>) any(), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        Page<Order> result = orderService.getOrdersWithFilters(
                1L, null, null, null, null, null, true, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    // ==================== createOrder ====================

    @Test
    @DisplayName("createOrder — generates order number and saves")
    void createOrder_success() {
        when(dailyOrderSequenceService.generateNextOrderNumber()).thenReturn("ORD-20260329-0001");
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.createOrder(order);

        assertEquals("ORD-20260329-0001", result.getOrderNumber());
        verify(orderRepository).save(any(Order.class));
    }

    // ==================== updateOrderStatus ====================

    @Test
    @DisplayName("updateOrderStatus — valid transition")
    void updateOrderStatus_validTransition() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(inventoryService.checkIngredientAvailability(any())).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.updateOrderStatus(1L, OrderStatus.ACCEPTED, "Accepted", "admin");

        assertEquals(OrderStatus.ACCEPTED, result.getStatus());
    }

    @Test
    @DisplayName("updateOrderStatus — accepting an order puts it on the kitchen board (online/bot orders reach the KDS)")
    void updateOrderStatus_accepted_createsKitchenTicket() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(inventoryService.checkIngredientAvailability(any())).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        orderService.updateOrderStatus(1L, OrderStatus.ACCEPTED, "Accepted", "operator");

        // Online / Instagram / self-service orders never go through a POS/waiter send-to-kitchen step;
        // acceptance is where they land on the kitchen display.
        verify(kitchenOrderService).createKitchenOrderIfAbsent(order);
    }

    @Test
    @DisplayName("updateOrderStatus — order not found throws")
    void updateOrderStatus_notFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.updateOrderStatus(99L, OrderStatus.ACCEPTED, null, "admin"));
    }

    @Test
    @DisplayName("updateOrderStatus — a cancellation pushes the order.cancelled event to the customer's tracking")
    void updateOrderStatus_cancelled_broadcastsToCustomer() {
        order.setStatus(OrderStatus.ACCEPTED); // ACCEPTED -> CANCELLED is a valid transition
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        orderService.updateOrderStatus(1L, OrderStatus.CANCELLED, "out of stock", "admin");

        // Previously only ACCEPTED broadcast to the customer; admin reject/cancel gave them no
        // live-tracking event. This is what closes that gap.
        verify(orderEventBroadcaster).broadcastOrderCancelled(order);
    }

    // The order from the factory has no linked customer (walk-in / dine-in), so these also prove the
    // broadcasts tolerate a null customer — before the fix they NPE'd on order.getCustomer().getId().

    @Test
    @DisplayName("updateOrderStatus — PREPARING pushes the order.preparing tracking event")
    void updateOrderStatus_preparing_broadcasts() {
        order.setStatus(OrderStatus.ACCEPTED); // ACCEPTED -> PREPARING is valid
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        orderService.updateOrderStatus(1L, OrderStatus.PREPARING, null, "kitchen");

        verify(orderEventBroadcaster).broadcastOrderPreparing(order);
    }

    @Test
    @DisplayName("updateOrderStatus — READY pushes the order.ready tracking event")
    void updateOrderStatus_ready_broadcasts() {
        order.setStatus(OrderStatus.PREPARING); // PREPARING -> READY is valid
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        orderService.updateOrderStatus(1L, OrderStatus.READY, null, "kitchen");

        verify(orderEventBroadcaster).broadcastOrderReady(order);
    }

    @Test
    @DisplayName("updateOrderStatus — PICKED_UP pushes the order.picked_up tracking event")
    void updateOrderStatus_pickedUp_broadcasts() {
        order.setStatus(OrderStatus.READY); // READY -> PICKED_UP is valid
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        orderService.updateOrderStatus(1L, OrderStatus.PICKED_UP, null, "courier");

        verify(orderEventBroadcaster).broadcastOrderPickedUp(order);
    }

    @Test
    @DisplayName("updateOrderStatus — COMPLETED pushes the order.completed tracking event")
    void updateOrderStatus_completed_broadcasts() {
        order.setStatus(OrderStatus.PICKED_UP); // PICKED_UP -> COMPLETED is valid
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        orderService.updateOrderStatus(1L, OrderStatus.COMPLETED, null, "admin");

        verify(orderEventBroadcaster).broadcastOrderCompleted(order);
    }

    // ==================== revertOrderToActive ====================

    @Test
    @DisplayName("revertOrderToActive — from DELIVERED to READY")
    void revertOrder_fromDelivered_toReady() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(1L)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.revertOrderToActive(1L, OrderStatus.READY, "Mistake", "admin");

        assertEquals(OrderStatus.READY, result.getStatus());
    }

    @Test
    @DisplayName("revertOrderToActive — non-closed order throws")
    void revertOrder_nonClosed_throws() {
        order.setStatus(OrderStatus.PREPARING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(Exception.class,
                () -> orderService.revertOrderToActive(1L, OrderStatus.READY, "test", "admin"));
    }
}
