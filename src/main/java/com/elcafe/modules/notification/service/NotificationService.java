package com.elcafe.modules.notification.service;

import com.elcafe.modules.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Notification Service for sending notifications to various stakeholders
 * Supports multiple channels: WebSocket, SMS, Email, Push Notifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /**
     * Notify all relevant parties when a new order is placed
     * - Restaurant operators/kitchen staff
     * - Admin panel
     * - Optional: Customer confirmation
     */
    public void notifyNewOrder(Order order) {
        log.info("🔔 New Order Notification: {} for restaurant {}",
                order.getOrderNumber(), order.getRestaurant().getName());

        // Notify restaurant operators
        notifyRestaurantOperators(order, "NEW_ORDER");

        // Notify kitchen
        notifyKitchen(order, "NEW_ORDER_FOR_PREPARATION");

        // Notify customer
        notifyCustomer(order, "ORDER_CONFIRMED");

        // Broadcast to admin panel
        broadcastToAdmins(order, "NEW_ORDER_RECEIVED");
    }

    /**
     * Notify when order is accepted by restaurant
     */
    public void notifyOrderAccepted(Order order) {
        log.info("✅ Order Accepted: {}", order.getOrderNumber());

        notifyCustomer(order, "ORDER_ACCEPTED");
        notifyKitchen(order, "START_PREPARATION");
    }

    /**
     * Notify when order is being prepared
     */
    public void notifyOrderPreparing(Order order) {
        log.info("👨‍🍳 Order Preparing: {}", order.getOrderNumber());

        notifyCustomer(order, "ORDER_PREPARING");
        notifyCouriers(order, "ORDER_WILL_BE_READY_SOON");
    }

    /**
     * Notify when order is ready for pickup/delivery
     */
    public void notifyOrderReady(Order order) {
        log.info("✅ Order Ready: {}", order.getOrderNumber());

        notifyCustomer(order, "ORDER_READY");
        notifyCouriers(order, "ORDER_READY_FOR_PICKUP");
        broadcastToAdmins(order, "ORDER_READY_FOR_DELIVERY");
    }

    /**
     * Notify when courier is assigned
     */
    public void notifyCourierAssigned(Order order, Long courierId, String courierName) {
        log.info("🚗 Courier Assigned: {} to order {}", courierName, order.getOrderNumber());

        notifyCustomer(order, "COURIER_ASSIGNED");
        notifySpecificCourier(courierId, order, "ORDER_ASSIGNED_TO_YOU");
        notifyKitchen(order, "COURIER_ASSIGNED");
    }

    /**
     * Notify when order is out for delivery
     */
    public void notifyOrderOnDelivery(Order order) {
        log.info("🚚 Order Out for Delivery: {}", order.getOrderNumber());

        notifyCustomer(order, "ORDER_ON_THE_WAY");
        broadcastToAdmins(order, "ORDER_OUT_FOR_DELIVERY");
    }

    /**
     * Notify when order is delivered
     */
    public void notifyOrderDelivered(Order order) {
        log.info("✅ Order Delivered: {}", order.getOrderNumber());

        notifyCustomer(order, "ORDER_DELIVERED");
        notifyRestaurantOperators(order, "ORDER_COMPLETED");
        broadcastToAdmins(order, "ORDER_DELIVERED");
    }

    /**
     * Notify when order is cancelled
     */
    public void notifyOrderCancelled(Order order) {
        log.info("❌ Order Cancelled: {}", order.getOrderNumber());

        notifyRestaurantOperators(order, "ORDER_CANCELLED");
        notifyKitchen(order, "ORDER_CANCELLED");
        if (order.getDeliveryInfo() != null && order.getDeliveryInfo().getCourierId() != null) {
            notifySpecificCourier(order.getDeliveryInfo().getCourierId(), order, "ORDER_CANCELLED");
        }
        broadcastToAdmins(order, "ORDER_CANCELLED");
    }

    /**
     * Notify when courier accepts an order
     */
    public void notifyCourierAccepted(Order order, String courierName) {
        log.info("✅ Courier {} accepted order {}", courierName, order.getOrderNumber());

        notifyCustomer(order, "COURIER_ACCEPTED_ORDER");
        notifyRestaurantOperators(order, "COURIER_ACCEPTED");
        broadcastToAdmins(order, "COURIER_ACCEPTED_ORDER");
    }

    /**
     * Notify when courier declines an order
     */
    public void notifyCourierDeclined(Order order, String courierName, String reason) {
        log.info("❌ Courier {} declined order {}: {}", courierName, order.getOrderNumber(), reason);

        notifyRestaurantOperators(order, "COURIER_DECLINED");
        broadcastToAdmins(order, "COURIER_DECLINED_ORDER");
        // Find another courier
        notifyCouriers(order, "ORDER_NEEDS_COURIER");
    }

    // Private helper methods for different notification channels

    private void notifyRestaurantOperators(Order order, String eventType) {
        // TODO: Implement WebSocket notification to restaurant operators
        log.debug("📤 Notifying restaurant operators for order {}: {}", order.getOrderNumber(), eventType);
    }

    private void notifyKitchen(Order order, String eventType) {
        // TODO: Implement WebSocket notification to kitchen staff
        log.debug("📤 Notifying kitchen for order {}: {}", order.getOrderNumber(), eventType);
    }

    private void notifyCustomer(Order order, String eventType) {
        // TODO: Implement customer notification via SMS/Email/Push
        log.debug("📤 Notifying customer for order {}: {}", order.getOrderNumber(), eventType);
    }

    private void notifyCouriers(Order order, String eventType) {
        // TODO: Implement broadcast to available couriers
        log.debug("📤 Notifying available couriers for order {}: {}", order.getOrderNumber(), eventType);
    }

    private void notifySpecificCourier(Long courierId, Order order, String eventType) {
        // TODO: Implement notification to specific courier
        log.debug("📤 Notifying courier {} for order {}: {}", courierId, order.getOrderNumber(), eventType);
    }

    private void broadcastToAdmins(Order order, String eventType) {
        // TODO: Implement WebSocket broadcast to admin panel
        log.debug("📤 Broadcasting to admins for order {}: {}", order.getOrderNumber(), eventType);
    }
}
