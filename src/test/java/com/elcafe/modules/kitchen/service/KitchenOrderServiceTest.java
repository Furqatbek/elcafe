package com.elcafe.modules.kitchen.service;

import com.elcafe.common.audit.service.AuditService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.enums.KitchenOrderStatus;
import com.elcafe.modules.kitchen.repository.KitchenOrderRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The KDS renders {@code kitchen_orders} rows, so {@code createKitchenOrderIfAbsent} is what puts an
 * order onto the board. It must be idempotent — the dine-in submit paths can invoke it more than once
 * for the same order and must never insert a duplicate ticket.
 */
@ExtendWith(MockitoExtension.class)
class KitchenOrderServiceTest {

    @Mock private KitchenOrderRepository kitchenOrderRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private NotificationService notificationService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @Mock private AuditService auditService;

    @InjectMocks private KitchenOrderService kitchenOrderService;

    @Test
    @DisplayName("createKitchenOrderIfAbsent — creates a PENDING ticket when the order has none")
    void createIfAbsent_createsWhenMissing() {
        Order order = createOrder(1L, OrderStatus.PREPARING);
        when(kitchenOrderRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(kitchenOrderRepository.save(any(KitchenOrder.class))).thenAnswer(i -> i.getArgument(0));

        KitchenOrder result = kitchenOrderService.createKitchenOrderIfAbsent(order);

        assertThat(result.getStatus()).isEqualTo(KitchenOrderStatus.PENDING);
        assertThat(result.getOrder()).isSameAs(order);
        verify(kitchenOrderRepository).save(any(KitchenOrder.class));
    }

    @Test
    @DisplayName("createKitchenOrderIfAbsent — returns the existing ticket, never a duplicate row")
    void createIfAbsent_idempotentWhenPresent() {
        Order order = createOrder(1L, OrderStatus.PREPARING);
        KitchenOrder existing = KitchenOrder.builder()
                .order(order)
                .status(KitchenOrderStatus.PREPARING)
                .build();
        when(kitchenOrderRepository.findByOrderId(1L)).thenReturn(Optional.of(existing));

        KitchenOrder result = kitchenOrderService.createKitchenOrderIfAbsent(order);

        assertThat(result).isSameAs(existing);
        verify(kitchenOrderRepository, never()).save(any(KitchenOrder.class));
    }

    // A kitchen ticket runs its own PENDING → PREPARING → READY lifecycle. It mirrors that onto the
    // parent order only while the order is still active — never onto a settled (paid) order, or an
    // auto-paid quick-sale's kitchen work would drag its COMPLETED order back to PREPARING/READY.

    @Test
    @DisplayName("markAsReady — an active order is advanced to READY alongside its ticket")
    void markAsReady_activeOrder_advancesOrder() {
        Order order = createOrder(1L, OrderStatus.PREPARING);
        KitchenOrder ticket = KitchenOrder.builder().order(order).status(KitchenOrderStatus.PREPARING).build();
        when(kitchenOrderRepository.findByIdWithOrder(3L)).thenReturn(Optional.of(ticket));
        when(kitchenOrderRepository.save(any(KitchenOrder.class))).thenAnswer(i -> i.getArgument(0));

        KitchenOrder result = kitchenOrderService.markAsReady(3L);

        assertThat(result.getStatus()).isEqualTo(KitchenOrderStatus.READY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("markAsReady — a settled quick-sale order stays COMPLETED while its ticket advances")
    void markAsReady_settledOrder_leavesOrderStatus() {
        Order order = createOrder(1L, OrderStatus.COMPLETED);
        KitchenOrder ticket = KitchenOrder.builder().order(order).status(KitchenOrderStatus.PREPARING).build();
        when(kitchenOrderRepository.findByIdWithOrder(3L)).thenReturn(Optional.of(ticket));
        when(kitchenOrderRepository.save(any(KitchenOrder.class))).thenAnswer(i -> i.getArgument(0));

        KitchenOrder result = kitchenOrderService.markAsReady(3L);

        assertThat(result.getStatus()).isEqualTo(KitchenOrderStatus.READY); // ticket advances
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);     // paid order untouched
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("startPreparation — a settled (DELIVERED) order is left untouched")
    void startPreparation_settledOrder_leavesOrderStatus() {
        Order order = createOrder(1L, OrderStatus.DELIVERED);
        KitchenOrder ticket = KitchenOrder.builder().order(order).status(KitchenOrderStatus.PENDING).build();
        when(kitchenOrderRepository.findByIdWithOrder(3L)).thenReturn(Optional.of(ticket));
        when(kitchenOrderRepository.save(any(KitchenOrder.class))).thenAnswer(i -> i.getArgument(0));

        kitchenOrderService.startPreparation(3L, "Chef Ana");

        assertThat(ticket.getStatus()).isEqualTo(KitchenOrderStatus.PREPARING); // ticket advances
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);         // paid order untouched
        verify(orderRepository, never()).save(any(Order.class));
    }
}
