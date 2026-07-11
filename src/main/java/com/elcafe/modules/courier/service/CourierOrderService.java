package com.elcafe.modules.courier.service;

import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.repository.CourierProfileRepository;
import com.elcafe.modules.kitchen.service.KitchenOrderService;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderStatusHistory;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.OrderJsonHydration;
import com.elcafe.modules.marketing.event.OrderCompletionEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierOrderService {

    /**
     * Upper bound for the cross-tenant "available orders" scan (audit PERF-9): the no-restaurant variant
     * otherwise grows with total platform volume. More simultaneously-READY orders than this is not a
     * pick-list a courier can act on anyway; newest first.
     */
    private static final int AVAILABLE_ORDERS_CAP = 200;

    private final OrderRepository orderRepository;
    private final OrderCompletionEvents orderCompletionEvents;
    private final CourierProfileRepository courierProfileRepository;
    private final NotificationService notificationService;
    private final KitchenOrderService kitchenOrderService;
    private final CourierWalletService courierWalletService;

    /**
     * Get orders that are ready for courier assignment. The per-restaurant list is naturally small and
     * stays uncapped; the cross-tenant list is capped at {@value #AVAILABLE_ORDERS_CAP} newest.
     */
    @Transactional(readOnly = true)
    public List<Order> getAvailableOrders(Long restaurantId) {
        if (restaurantId != null) {
            return OrderJsonHydration.forJson(
                    orderRepository.findByRestaurant_IdAndStatus(restaurantId, OrderStatus.READY));
        }
        return OrderJsonHydration.forJson(orderRepository.findByStatus(OrderStatus.READY,
                PageRequest.of(0, AVAILABLE_ORDERS_CAP, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    /**
     * Get orders assigned to a specific courier
     */
    @Transactional(readOnly = true)
    public List<Order> getCourierOrders(Long courierId) {
        return OrderJsonHydration.forJson(orderRepository.findByDeliveryInfo_CourierId(courierId));
    }

    /**
     * Courier accepts an order for delivery
     */
    @Transactional
    public Order acceptOrder(Long orderId, Long courierId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        CourierProfile courier = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found"));

        // Check if order is in correct status
        if (order.getStatus() != OrderStatus.READY) {
            throw new RuntimeException("Order is not ready for pickup");
        }

        // Check if courier is already assigned
        if (order.getDeliveryInfo().getCourierId() != null) {
            throw new RuntimeException("Order already has a courier assigned");
        }

        // Assign courier
        order.setStatus(OrderStatus.COURIER_ASSIGNED);
        order.getDeliveryInfo().setCourierId(courier.getId());
        order.getDeliveryInfo().setCourierName(courier.getUser().getFirstName() + " " + courier.getUser().getLastName());
        order.getDeliveryInfo().setCourierPhone(courier.getUser().getPhone());

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.COURIER_ASSIGNED)
                .changedBy("COURIER_" + courierId)
                .notes("Courier accepted order")
                .build();
        order.addStatusHistory(statusHistory);

        Order savedOrder = orderRepository.save(order);

        // Notify
        notificationService.notifyCourierAccepted(savedOrder, courier.getUser().getFirstName());

        log.info("Courier {} accepted order {}", courierId, order.getOrderNumber());
        return OrderJsonHydration.forJson(savedOrder);
    }

    /**
     * Courier declines an order
     */
    public void declineOrder(Long orderId, Long courierId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        CourierProfile courier = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found"));

        // Notify about decline
        notificationService.notifyCourierDeclined(order, courier.getUser().getFirstName(), reason);

        log.info("Courier {} declined order {}: {}", courierId, order.getOrderNumber(), reason);
    }

    /**
     * Admin/Operator manually assigns a courier to an order
     */
    @Transactional
    public Order assignCourier(Long orderId, Long courierId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        CourierProfile courier = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found"));

        order.setStatus(OrderStatus.COURIER_ASSIGNED);
        order.getDeliveryInfo().setCourierId(courier.getId());
        order.getDeliveryInfo().setCourierName(courier.getUser().getFirstName() + " " + courier.getUser().getLastName());
        order.getDeliveryInfo().setCourierPhone(courier.getUser().getPhone());

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.COURIER_ASSIGNED)
                .changedBy("OPERATOR")
                .notes("Courier manually assigned")
                .build();
        order.addStatusHistory(statusHistory);

        Order savedOrder = orderRepository.save(order);

        // Notify
        notificationService.notifyCourierAssigned(savedOrder, courier.getId(), courier.getUser().getFirstName());

        log.info("Courier {} manually assigned to order {}", courierId, order.getOrderNumber());
        return OrderJsonHydration.forJson(savedOrder);
    }

    /**
     * Courier starts delivery
     */
    @Transactional
    public Order startDelivery(Long orderId, Long courierId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Verify courier
        if (!courierId.equals(order.getDeliveryInfo().getCourierId())) {
            throw new RuntimeException("This order is not assigned to you");
        }

        if (order.getStatus() != OrderStatus.COURIER_ASSIGNED) {
            throw new RuntimeException("Order is not in correct status");
        }

        order.setStatus(OrderStatus.ON_DELIVERY);
        order.getDeliveryInfo().setPickupTime(OffsetDateTime.now(ZoneOffset.UTC));
        order.getDeliveryInfo().setEstimatedDeliveryTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30));

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.ON_DELIVERY)
                .changedBy("COURIER_" + courierId)
                .notes("Order picked up, out for delivery")
                .build();
        order.addStatusHistory(statusHistory);

        Order savedOrder = orderRepository.save(order);

        // Mark as picked up in kitchen
        kitchenOrderService.markAsPickedUp(order.getId());

        // Notify
        notificationService.notifyOrderOnDelivery(savedOrder);

        log.info("Courier {} started delivery for order {}", courierId, order.getOrderNumber());
        return OrderJsonHydration.forJson(savedOrder);
    }

    /**
     * Courier completes delivery
     */
    @Transactional
    public Order completeDelivery(Long orderId, Long courierId, String notes) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Verify courier
        if (!courierId.equals(order.getDeliveryInfo().getCourierId())) {
            throw new RuntimeException("This order is not assigned to you");
        }

        if (order.getStatus() != OrderStatus.ON_DELIVERY) {
            throw new RuntimeException("Order is not out for delivery");
        }

        order.setStatus(OrderStatus.DELIVERED);
        order.getDeliveryInfo().setDeliveryTime(OffsetDateTime.now(ZoneOffset.UTC));

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.DELIVERED)
                .changedBy("COURIER_" + courierId)
                .notes(notes != null ? notes : "Order delivered successfully")
                .build();
        order.addStatusHistory(statusHistory);

        Order savedOrder = orderRepository.save(order);

        // Loyalty/marketing completion chain (audit FUNC-15): fires now if the delivery was already
        // paid (prepaid); an unpaid COD order fires later, when PaymentService records full payment.
        if (orderCompletionEvents != null) {
            orderCompletionEvents.publishIfQualified(savedOrder);
        }

        // Credit courier wallet for delivery
        try {
            courierWalletService.creditDeliveryFee(courierId, savedOrder);
        } catch (Exception e) {
            log.error("Failed to credit courier wallet for order {}: {}", savedOrder.getOrderNumber(), e.getMessage());
            // Continue with delivery completion even if wallet credit fails
        }

        // Notify
        notificationService.notifyOrderDelivered(savedOrder);

        log.info("Courier {} completed delivery for order {}", courierId, order.getOrderNumber());
        return OrderJsonHydration.forJson(savedOrder);
    }
}
