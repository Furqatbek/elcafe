package com.elcafe.modules.courier.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.repository.CourierProfileRepository;
import com.elcafe.modules.kitchen.service.KitchenOrderService;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.entity.DeliveryInfo;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourierOrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CourierProfileRepository courierProfileRepository;
    @Mock private NotificationService notificationService;
    @Mock private KitchenOrderService kitchenOrderService;
    @Mock private CourierWalletService courierWalletService;
    @Mock
    private com.elcafe.modules.marketing.event.OrderCompletionEvents orderCompletionEvents;

    @InjectMocks private CourierOrderService courierOrderService;

    private User user;
    private CourierProfile courier;
    private Order order;
    private DeliveryInfo deliveryInfo;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).firstName("Test").lastName("Courier").phone("+998901234567").build();
        courier = new CourierProfile();
        courier.setId(1L); courier.setUser(user);

        deliveryInfo = new DeliveryInfo();
        deliveryInfo.setCourierId(null); // unassigned

        order = new Order();
        order.setId(1L); order.setOrderNumber("ORD-001");
        order.setStatus(OrderStatus.READY);
        order.setDeliveryInfo(deliveryInfo);
        order.setStatusHistory(new ArrayList<>());
        order.setTotal(new BigDecimal("100000"));
    }

    @Test @DisplayName("getAvailableOrders — returns READY orders")
    void getAvailableOrders_returnsList() {
        when(orderRepository.findByRestaurant_IdAndStatus(1L, OrderStatus.READY)).thenReturn(List.of(order));
        List<Order> result = courierOrderService.getAvailableOrders(1L);
        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("getCourierOrders — returns assigned orders")
    void getCourierOrders_returnsList() {
        when(orderRepository.findByDeliveryInfo_CourierId(1L)).thenReturn(List.of(order));
        List<Order> result = courierOrderService.getCourierOrders(1L);
        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("acceptOrder — assigns courier and updates status")
    void acceptOrder_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(courier));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Order result = courierOrderService.acceptOrder(1L, 1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.COURIER_ASSIGNED);
        assertThat(result.getDeliveryInfo().getCourierId()).isEqualTo(1L);
        verify(notificationService).notifyCourierAccepted(any(), anyString());
    }

    @Test @DisplayName("acceptOrder — already assigned throws")
    void acceptOrder_alreadyAssigned_throws() {
        deliveryInfo.setCourierId(2L); // already assigned to another courier
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> courierOrderService.acceptOrder(1L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already has a courier");
    }

    @Test @DisplayName("declineOrder — logs decline")
    void declineOrder_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(courier));

        courierOrderService.declineOrder(1L, 1L, "Too far");

        verify(notificationService).notifyCourierDeclined(any(), anyString(), eq("Too far"));
    }

    @Test @DisplayName("assignCourier — admin assigns")
    void assignCourier_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(courierProfileRepository.findById(1L)).thenReturn(Optional.of(courier));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Order result = courierOrderService.assignCourier(1L, 1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.COURIER_ASSIGNED);
        verify(notificationService).notifyCourierAssigned(any(), eq(1L), anyString());
    }

    @Test @DisplayName("startDelivery — sets ON_DELIVERY status")
    void startDelivery_success() {
        order.setStatus(OrderStatus.COURIER_ASSIGNED);
        deliveryInfo.setCourierId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Order result = courierOrderService.startDelivery(1L, 1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.ON_DELIVERY);
        assertThat(result.getDeliveryInfo().getPickupTime()).isNotNull();
        verify(kitchenOrderService).markAsPickedUp(1L);
    }

    @Test @DisplayName("completeDelivery — sets DELIVERED and credits wallet")
    void completeDelivery_success() {
        order.setStatus(OrderStatus.ON_DELIVERY);
        deliveryInfo.setCourierId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Order result = courierOrderService.completeDelivery(1L, 1L, "Delivered to doorstep");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(result.getDeliveryInfo().getDeliveryTime()).isNotNull();
        verify(courierWalletService).creditDeliveryFee(eq(1L), any());
        // Delivery is a settling moment: the completion gate must be consulted (fires iff paid).
        verify(orderCompletionEvents).publishIfQualified(any(Order.class));
    }
}
