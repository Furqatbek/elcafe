package com.elcafe.modules.courier.service;

import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.repository.CourierProfileRepository;
import com.elcafe.modules.kitchen.service.KitchenOrderService;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderStatusHistory;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierOrderService {

    private final OrderRepository orderRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final NotificationService notificationService;
    private final KitchenOrderService kitchenOrderService;

    /**
     * Get orders that are ready for courier assignment
     */
    public List<Order> getAvailableOrders(Long restaurantId) {
        if (restaurantId != null) {
            return orderRepository.findByRestaurantIdAndStatus(restaurantId, OrderStatus.READY);
        }
        return orderRepository.findByStatus(OrderStatus.READY);
    }

    /**
     * Get orders assigned to a specific courier
     */
    public List<Order> getCourierOrders(Long courierId) {
        return orderRepository.findByCourierId(courierId);
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
        return savedOrder;
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
        return savedOrder;
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
        order.getDeliveryInfo().setPickupTime(LocalDateTime.now());
        order.getDeliveryInfo().setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(30));

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
        return savedOrder;
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
        order.getDeliveryInfo().setDeliveryTime(LocalDateTime.now());

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.DELIVERED)
                .changedBy("COURIER_" + courierId)
                .notes(notes != null ? notes : "Order delivered successfully")
                .build();
        order.addStatusHistory(statusHistory);

        Order savedOrder = orderRepository.save(order);

        // Notify
        notificationService.notifyOrderDelivered(savedOrder);

        log.info("Courier {} completed delivery for order {}", courierId, order.getOrderNumber());
        return savedOrder;
    }
}
