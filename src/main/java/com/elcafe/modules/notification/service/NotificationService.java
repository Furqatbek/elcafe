package com.elcafe.modules.notification.service;

import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.enums.NotificationType;
import com.elcafe.modules.notification.enums.UserRole;
import com.elcafe.modules.notification.repository.NotificationRepository;
import com.elcafe.modules.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notification Service for sending notifications to various stakeholders
 * Creates persistent notification records in database for all user roles
 */
@Slf4j
@Service
@Lazy
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Notify all relevant parties when a new order is placed
     * - Restaurant operators/kitchen staff
     * - Admin panel
     * - Optional: Customer confirmation
     */
    @Transactional
    public void notifyNewOrder(Order order) {
        log.info("🔔 New Order Notification: {} for restaurant {}",
                order.getOrderNumber(), order.getRestaurant().getName());

        // Notify restaurant operators
        createNotification(
            order,
            UserRole.RESTAURANT,
            order.getRestaurant().getId(),
            NotificationType.NEW_ORDER,
            "New Order Received",
            String.format("New order #%s received. Total: $%.2f",
                order.getOrderNumber(), order.getTotal()),
            1 // High priority
        );

        // Notify kitchen
        createNotification(
            order,
            UserRole.KITCHEN,
            order.getRestaurant().getId(),
            NotificationType.NEW_ORDER_FOR_PREPARATION,
            "New Order for Preparation",
            String.format("Order #%s needs preparation. %d items.",
                order.getOrderNumber(), order.getItems().size()),
            1 // High priority
        );

        // Notify customer (only if customer exists - not for POS/walk-in orders)
        if (order.getCustomer() != null) {
            createNotification(
                order,
                UserRole.CUSTOMER,
                order.getCustomer().getId(),
                NotificationType.ORDER_CONFIRMED,
                "Order Confirmed",
                String.format("Your order #%s has been placed successfully. Total: $%.2f",
                    order.getOrderNumber(), order.getTotal()),
                2
            );
        }

        // Broadcast to admin panel
        createNotification(
            order,
            UserRole.ADMIN,
            null, // Broadcast to all admins
            NotificationType.NEW_ORDER_RECEIVED,
            "New Order Received",
            String.format("New order #%s from %s. Total: $%.2f",
                order.getOrderNumber(), order.getRestaurant().getName(), order.getTotal()),
            2
        );
    }

    /**
     * Notify when order is accepted by restaurant
     */
    @Transactional
    public void notifyOrderAccepted(Order order) {
        log.info("✅ Order Accepted: {}", order.getOrderNumber());

        if (order.getCustomer() != null) {
            createNotification(
                order,
                UserRole.CUSTOMER,
                order.getCustomer().getId(),
                NotificationType.ORDER_ACCEPTED,
                "Order Accepted",
                String.format("Your order #%s has been accepted by %s",
                    order.getOrderNumber(), order.getRestaurant().getName()),
                2
            );
        }

        createNotification(
            order,
            UserRole.KITCHEN,
            order.getRestaurant().getId(),
            NotificationType.START_PREPARATION,
            "Start Preparation",
            String.format("Order #%s is accepted. Start preparing %d items.",
                order.getOrderNumber(), order.getItems().size()),
            1
        );
    }

    /**
     * Notify when order is being prepared
     */
    @Transactional
    public void notifyOrderPreparing(Order order) {
        log.info("👨‍🍳 Order Preparing: {}", order.getOrderNumber());

        if (order.getCustomer() != null) {
            createNotification(
                order,
                UserRole.CUSTOMER,
                order.getCustomer().getId(),
                NotificationType.ORDER_PREPARING,
                "Order Being Prepared",
                String.format("Your order #%s is being prepared by the kitchen",
                    order.getOrderNumber()),
                3
            );
        }

        createNotification(
            order,
            UserRole.COURIER,
            null, // Broadcast to available couriers
            NotificationType.ORDER_WILL_BE_READY_SOON,
            "Order Will Be Ready Soon",
            String.format("Order #%s will be ready soon for %s",
                order.getOrderNumber(), order.getOrderType()),
            3
        );
    }

    /**
     * Notify when order is ready for pickup/delivery
     */
    @Transactional
    public void notifyOrderReady(Order order) {
        log.info("✅ Order Ready: {}", order.getOrderNumber());

        if (order.getCustomer() != null) {
            createNotification(
                order,
                UserRole.CUSTOMER,
                order.getCustomer().getId(),
                NotificationType.ORDER_READY,
                "Order Ready",
                String.format("Your order #%s is ready for %s",
                    order.getOrderNumber(), order.getOrderType().name().toLowerCase()),
                1
            );
        }

        createNotification(
            order,
            UserRole.COURIER,
            null, // Broadcast to available couriers
            NotificationType.ORDER_READY_FOR_PICKUP,
            "Order Ready for Pickup",
            String.format("Order #%s is ready for pickup from %s",
                order.getOrderNumber(), order.getRestaurant().getName()),
            2
        );

        createNotification(
            order,
            UserRole.ADMIN,
            null,
            NotificationType.ORDER_READY_FOR_DELIVERY,
            "Order Ready for Delivery",
            String.format("Order #%s is ready for delivery",
                order.getOrderNumber()),
            3
        );
    }

    /**
     * Notify when order is rejected by restaurant
     */
    @Transactional
    public void notifyOrderRejected(Order order) {
        log.info("❌ Order Rejected: {}", order.getOrderNumber());

        if (order.getCustomer() != null) {
            createNotification(
                order,
                UserRole.CUSTOMER,
                order.getCustomer().getId(),
                NotificationType.ORDER_REJECTED,
                "Order Rejected",
                String.format("Unfortunately, your order #%s has been rejected. You will receive a refund.",
                    order.getOrderNumber()),
                1
            );
        }

        createNotification(
            order,
            UserRole.ADMIN,
            null,
            NotificationType.ORDER_REJECTED,
            "Order Rejected",
            String.format("Order #%s has been rejected by %s",
                order.getOrderNumber(), order.getRestaurant().getName()),
            2
        );
    }

    /**
     * Notify when order is completed
     */
    @Transactional
    public void notifyOrderCompleted(Order order) {
        log.info("✅ Order Completed: {}", order.getOrderNumber());

        if (order.getCustomer() != null) {
            createNotification(
                order,
                UserRole.CUSTOMER,
                order.getCustomer().getId(),
                NotificationType.ORDER_COMPLETED,
                "Order Completed",
                String.format("Your order #%s has been completed. Thank you!",
                    order.getOrderNumber()),
                3
            );
        }

        createNotification(
            order,
            UserRole.RESTAURANT,
            order.getRestaurant().getId(),
            NotificationType.ORDER_COMPLETED,
            "Order Completed",
            String.format("Order #%s has been completed",
                order.getOrderNumber()),
            3
        );

        createNotification(
            order,
            UserRole.ADMIN,
            null,
            NotificationType.ORDER_COMPLETED,
            "Order Completed",
            String.format("Order #%s has been completed successfully",
                order.getOrderNumber()),
            3
        );
    }

    /**
     * Notify when courier is assigned
     */
    @Transactional
    public void notifyCourierAssigned(Order order, Long courierId, String courierName) {
        log.info("🚗 Courier Assigned: {} to order {}", courierName, order.getOrderNumber());

        if (order.getCustomer() != null) {
            createNotification(
                order,
                UserRole.CUSTOMER,
                order.getCustomer().getId(),
                NotificationType.COURIER_ASSIGNED,
                "Courier Assigned",
                String.format("Courier %s has been assigned to your order #%s",
                    courierName, order.getOrderNumber()),
                2
            );
        }

        createNotification(
            order,
            UserRole.COURIER,
            courierId,
            NotificationType.ORDER_ASSIGNED_TO_YOU,
            "New Order Assigned",
            String.format("Order #%s has been assigned to you. Pickup from %s",
                order.getOrderNumber(), order.getRestaurant().getName()),
            1
        );
    }

    /**
     * Notify when order is picked up by courier
     */
    @Transactional
    public void notifyOrderPickedUp(Order order) {
        log.info("📦 Order Picked Up: {}", order.getOrderNumber());

        if (order.getCustomer() != null) {
            createNotification(
                order,
                UserRole.CUSTOMER,
                order.getCustomer().getId(),
                NotificationType.ORDER_PICKED_UP,
                "Order Picked Up",
                String.format("Your order #%s has been picked up and is on the way!",
                    order.getOrderNumber()),
                2
            );
        }

        createNotification(
            order,
            UserRole.RESTAURANT,
            order.getRestaurant().getId(),
            NotificationType.ORDER_PICKED_UP,
            "Order Picked Up",
            String.format("Order #%s has been picked up by courier",
                order.getOrderNumber()),
            3
        );
    }

    /**
     * Notify when order is out for delivery
     */
    @Transactional
    public void notifyOrderOnDelivery(Order order) {
        log.info("🚚 Order Out for Delivery: {}", order.getOrderNumber());

        if (order.getCustomer() != null) {
            createNotification(
                order,
                UserRole.CUSTOMER,
                order.getCustomer().getId(),
                NotificationType.ORDER_ON_THE_WAY,
                "Order On The Way",
                String.format("Your order #%s is on the way to you!",
                    order.getOrderNumber()),
                2
            );
        }

        createNotification(
            order,
            UserRole.ADMIN,
            null,
            NotificationType.ORDER_OUT_FOR_DELIVERY,
            "Order Out for Delivery",
            String.format("Order #%s is out for delivery",
                order.getOrderNumber()),
            3
        );
    }

    /**
     * Notify when order is delivered
     */
    @Transactional
    public void notifyOrderDelivered(Order order) {
        log.info("✅ Order Delivered: {}", order.getOrderNumber());

        if (order.getCustomer() != null) {
            createNotification(
                order,
                UserRole.CUSTOMER,
                order.getCustomer().getId(),
                NotificationType.ORDER_DELIVERED,
                "Order Delivered",
                String.format("Your order #%s has been delivered. Enjoy your meal!",
                    order.getOrderNumber()),
                2
            );
        }

        createNotification(
            order,
            UserRole.RESTAURANT,
            order.getRestaurant().getId(),
            NotificationType.ORDER_COMPLETED,
            "Order Delivered",
            String.format("Order #%s has been delivered successfully",
                order.getOrderNumber()),
            3
        );

        createNotification(
            order,
            UserRole.ADMIN,
            null,
            NotificationType.ORDER_DELIVERED,
            "Order Delivered",
            String.format("Order #%s has been delivered successfully",
                order.getOrderNumber()),
            3
        );
    }

    /**
     * Notify when order is cancelled
     */
    @Transactional
    public void notifyOrderCancelled(Order order) {
        log.info("❌ Order Cancelled: {}", order.getOrderNumber());

        createNotification(
            order,
            UserRole.RESTAURANT,
            order.getRestaurant().getId(),
            NotificationType.ORDER_CANCELLED,
            "Order Cancelled",
            String.format("Order #%s has been cancelled. Reason: %s",
                order.getOrderNumber(), order.getCancellationReason()),
            2
        );

        createNotification(
            order,
            UserRole.KITCHEN,
            order.getRestaurant().getId(),
            NotificationType.ORDER_CANCELLED,
            "Order Cancelled",
            String.format("Stop preparation! Order #%s has been cancelled.",
                order.getOrderNumber()),
            1
        );

        if (order.getDeliveryInfo() != null && order.getDeliveryInfo().getCourierId() != null) {
            createNotification(
                order,
                UserRole.COURIER,
                order.getDeliveryInfo().getCourierId(),
                NotificationType.ORDER_CANCELLED,
                "Order Cancelled",
                String.format("Order #%s has been cancelled",
                    order.getOrderNumber()),
                2
            );
        }

        createNotification(
            order,
            UserRole.ADMIN,
            null,
            NotificationType.ORDER_CANCELLED,
            "Order Cancelled",
            String.format("Order #%s has been cancelled by %s",
                order.getOrderNumber(), order.getCancelledBy()),
            2
        );
    }

    /**
     * Notify when courier accepts an order
     */
    @Transactional
    public void notifyCourierAccepted(Order order, String courierName) {
        log.info("✅ Courier {} accepted order {}", courierName, order.getOrderNumber());

        if (order.getCustomer() != null) {
            createNotification(
                order,
                UserRole.CUSTOMER,
                order.getCustomer().getId(),
                NotificationType.COURIER_ACCEPTED_ORDER,
                "Courier Accepted",
                String.format("Courier %s has accepted your order #%s",
                    courierName, order.getOrderNumber()),
                2
            );
        }

        createNotification(
            order,
            UserRole.RESTAURANT,
            order.getRestaurant().getId(),
            NotificationType.COURIER_ACCEPTED_ORDER,
            "Courier Accepted",
            String.format("Courier %s accepted order #%s",
                courierName, order.getOrderNumber()),
            3
        );

        createNotification(
            order,
            UserRole.ADMIN,
            null,
            NotificationType.COURIER_ACCEPTED_ORDER,
            "Courier Accepted Order",
            String.format("Courier %s accepted order #%s",
                courierName, order.getOrderNumber()),
            3
        );
    }

    /**
     * Notify when courier declines an order
     */
    @Transactional
    public void notifyCourierDeclined(Order order, String courierName, String reason) {
        log.info("❌ Courier {} declined order {}: {}", courierName, order.getOrderNumber(), reason);

        createNotification(
            order,
            UserRole.RESTAURANT,
            order.getRestaurant().getId(),
            NotificationType.COURIER_DECLINED_ORDER,
            "Courier Declined",
            String.format("Courier %s declined order #%s. Reason: %s",
                courierName, order.getOrderNumber(), reason),
            2
        );

        createNotification(
            order,
            UserRole.ADMIN,
            null,
            NotificationType.COURIER_DECLINED_ORDER,
            "Courier Declined Order",
            String.format("Courier %s declined order #%s",
                courierName, order.getOrderNumber()),
            2
        );

        // Notify available couriers
        createNotification(
            order,
            UserRole.COURIER,
            null,
            NotificationType.ORDER_NEEDS_COURIER,
            "Order Needs Courier",
            String.format("Order #%s needs a courier. Previous courier declined.",
                order.getOrderNumber()),
            1
        );
    }

    /**
     * Helper method to create and save a notification
     */
    private void createNotification(
        Order order,
        UserRole userRole,
        Long userId,
        NotificationType type,
        String title,
        String message,
        Integer priority
    ) {
        Notification notification = Notification.builder()
            .restaurantId(order.getRestaurant().getId())
            .userRole(userRole)
            .userId(userId)
            .type(type)
            .title(title)
            .message(message)
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .priority(priority)
            .build();

        notificationRepository.save(notification);
        log.debug("📤 Created notification for {} (userId: {}): {}",
            userRole, userId, title);
    }
}
