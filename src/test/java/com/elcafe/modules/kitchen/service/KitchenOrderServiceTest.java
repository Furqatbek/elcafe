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
}
