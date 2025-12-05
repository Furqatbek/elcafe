package com.elcafe.modules.order.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderStatusHistory;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.validator.OrderStatusTransitionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusTransitionValidator statusTransitionValidator;
    private final OrderEventBroadcaster orderEventBroadcaster;
    private final com.elcafe.modules.notification.service.NotificationService notificationService;

    @Transactional
    public Order createOrder(Order order) {
        log.info("Creating new order");

        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.NEW);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(OrderStatus.NEW)
                .changedBy("SYSTEM")
                .notes("Order created")
                .build();
        order.addStatusHistory(history);

        order = orderRepository.save(order);
        log.info("Order created with number: {}", order.getOrderNumber());

        return order;
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus, String notes, String changedBy) {
        log.info("Updating order {} to status: {}", orderId, newStatus);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        OrderStatus currentStatus = order.getStatus();
        if (!isValidStatusTransition(currentStatus, newStatus)) {
            throw new BadRequestException(
                    String.format("Invalid status transition from %s to %s", currentStatus, newStatus)
            );
        }

        order.setStatus(newStatus);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(newStatus)
                .changedBy(changedBy)
                .notes(notes)
                .build();
        order.addStatusHistory(history);

        order = orderRepository.save(order);
        log.info("Order status updated: {} -> {}", currentStatus, newStatus);

        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
    }

    @Transactional(readOnly = true)
    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
    }

    @Transactional(readOnly = true)
    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByRestaurant(Long restaurantId) {
        return orderRepository.findByRestaurantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                restaurantId,
                LocalDateTime.now().minusDays(7),
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Order> getPendingOrders() {
        return orderRepository.findByStatusOrderByCreatedAtAsc(OrderStatus.NEW);
    }

    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private boolean isValidStatusTransition(OrderStatus current, OrderStatus next) {
        return switch (current) {
            case PENDING -> next == OrderStatus.PLACED || next == OrderStatus.CANCELLED;
            case PLACED -> next == OrderStatus.NEW || next == OrderStatus.ACCEPTED || next == OrderStatus.REJECTED || next == OrderStatus.CANCELLED;
            case NEW -> next == OrderStatus.ACCEPTED || next == OrderStatus.REJECTED || next == OrderStatus.CANCELLED;
            case ACCEPTED -> next == OrderStatus.PREPARING || next == OrderStatus.CANCELLED;
            case REJECTED -> false;
            case PREPARING -> next == OrderStatus.READY || next == OrderStatus.CANCELLED;
            case READY -> next == OrderStatus.PICKED_UP || next == OrderStatus.COURIER_ASSIGNED || next == OrderStatus.CANCELLED;
            case PICKED_UP -> next == OrderStatus.COMPLETED || next == OrderStatus.ON_DELIVERY;
            case COURIER_ASSIGNED -> next == OrderStatus.ON_DELIVERY || next == OrderStatus.CANCELLED;
            case ON_DELIVERY -> next == OrderStatus.DELIVERED || next == OrderStatus.COMPLETED;
            case DELIVERED -> next == OrderStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /**
     * Accept order - Admin action
     * Status: PLACED → ACCEPTED
     */
    @Transactional
    public Order acceptOrder(Long orderId, String acceptedBy, String notes) {
        log.info("Accepting order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Validate transition
        statusTransitionValidator.validateTransition(order.getStatus(), OrderStatus.ACCEPTED);

        // Update status and timestamp
        order.setStatus(OrderStatus.ACCEPTED);
        order.setAcceptedAt(LocalDateTime.now());

        // Add status history
        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(OrderStatus.ACCEPTED)
                .changedBy(acceptedBy)
                .notes(notes != null ? notes : "Order accepted by restaurant")
                .build();
        order.addStatusHistory(history);

        order = orderRepository.save(order);

        // Broadcast WebSocket event
        try {
            orderEventBroadcaster.broadcastOrderAccepted(order);
        } catch (Exception e) {
            log.error("Failed to broadcast order accepted event: {}", e.getMessage());
        }

        // Send SMS notification
        try {
            notificationService.notifyOrderAccepted(order);
        } catch (Exception e) {
            log.error("Failed to send order accepted SMS: {}", e.getMessage());
        }

        log.info("Order {} accepted successfully", order.getOrderNumber());
        return order;
    }

    /**
     * Reject order - Admin action
     * Status: PLACED → REJECTED
     * Automatically initiates refund
     */
    @Transactional
    public Order rejectOrder(Long orderId, String reason, String rejectedBy) {
        log.info("Rejecting order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Validate transition
        statusTransitionValidator.validateTransition(order.getStatus(), OrderStatus.REJECTED);

        // Update status and timestamp
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectedAt(LocalDateTime.now());

        // Add status history
        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(OrderStatus.REJECTED)
                .changedBy(rejectedBy)
                .notes("Order rejected: " + reason)
                .build();
        order.addStatusHistory(history);

        // Initiate refund if payment was completed
        if (order.getPayment() != null &&
            order.getPayment().getStatus() == com.elcafe.modules.order.enums.PaymentStatus.COMPLETED) {
            order.getPayment().setStatus(com.elcafe.modules.order.enums.PaymentStatus.REFUNDED);
            order.setPaymentStatus(com.elcafe.modules.order.enums.PaymentStatus.REFUNDED);
        }

        order = orderRepository.save(order);

        // Broadcast WebSocket event
        try {
            orderEventBroadcaster.broadcastOrderRejected(order, reason);
        } catch (Exception e) {
            log.error("Failed to broadcast order rejected event: {}", e.getMessage());
        }

        // Send SMS notification
        try {
            notificationService.notifyOrderRejected(order);
        } catch (Exception e) {
            log.error("Failed to send order rejected SMS: {}", e.getMessage());
        }

        log.info("Order {} rejected successfully", order.getOrderNumber());
        return order;
    }

    /**
     * Cancel order - Can be called by Consumer or Admin
     * Validates cancellation rules (5-minute window for consumers)
     */
    @Transactional
    public Order cancelOrder(Long orderId, String reason, String cancelledBy) {
        log.info("Cancelling order: {} by {}", orderId, cancelledBy);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Validate if order can be cancelled
        if (!statusTransitionValidator.canBeCancelled(order.getStatus())) {
            throw new BadRequestException(
                "Order cannot be cancelled at this stage. Current status: " + order.getStatus()
            );
        }

        // For consumer cancellations, check 5-minute time window
        if ("CONSUMER".equals(cancelledBy) && order.getPlacedAt() != null) {
            LocalDateTime fiveMinutesAfterPlacement = order.getPlacedAt().plusMinutes(5);
            if (LocalDateTime.now().isAfter(fiveMinutesAfterPlacement)) {
                throw new BadRequestException(
                    "Order can only be cancelled within 5 minutes of placement"
                );
            }
        }

        // Store cancellation details
        order.setCancellationReason(reason);
        order.setCancelledBy(cancelledBy);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());

        // Add status history
        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(OrderStatus.CANCELLED)
                .changedBy(cancelledBy)
                .notes("Order cancelled: " + reason)
                .build();
        order.addStatusHistory(history);

        // Initiate refund if payment was completed
        if (order.getPayment() != null &&
            order.getPayment().getStatus() == com.elcafe.modules.order.enums.PaymentStatus.COMPLETED) {
            order.getPayment().setStatus(com.elcafe.modules.order.enums.PaymentStatus.REFUNDED);
            order.setPaymentStatus(com.elcafe.modules.order.enums.PaymentStatus.REFUNDED);
        }

        order = orderRepository.save(order);

        // Broadcast WebSocket event
        try {
            orderEventBroadcaster.broadcastOrderCancelled(order);
        } catch (Exception e) {
            log.error("Failed to broadcast order cancelled event: {}", e.getMessage());
        }

        // Send SMS notification
        try {
            notificationService.notifyOrderCancelled(order);
        } catch (Exception e) {
            log.error("Failed to send order cancelled SMS: {}", e.getMessage());
        }

        log.info("Order {} cancelled successfully", order.getOrderNumber());
        return order;
    }

    /**
     * Mark order as preparing - Kitchen starts work
     * Status: ACCEPTED → PREPARING
     */
    @Transactional
    public Order markOrderPreparing(Long orderId, String notes) {
        log.info("Marking order as preparing: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Validate transition
        statusTransitionValidator.validateTransition(order.getStatus(), OrderStatus.PREPARING);

        // Update status and timestamp
        order.setStatus(OrderStatus.PREPARING);
        order.setPreparingAt(LocalDateTime.now());

        // Add status history
        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(OrderStatus.PREPARING)
                .changedBy("KITCHEN")
                .notes(notes != null ? notes : "Kitchen started preparing order")
                .build();
        order.addStatusHistory(history);

        order = orderRepository.save(order);

        // Broadcast WebSocket event
        try {
            orderEventBroadcaster.broadcastOrderPreparing(order);
        } catch (Exception e) {
            log.error("Failed to broadcast order preparing event: {}", e.getMessage());
        }

        log.info("Order {} marked as preparing", order.getOrderNumber());
        return order;
    }

    /**
     * Mark order as ready - Food is ready for pickup/delivery
     * Status: PREPARING → READY
     */
    @Transactional
    public Order markOrderReady(Long orderId, String notes) {
        log.info("Marking order as ready: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Validate transition
        statusTransitionValidator.validateTransition(order.getStatus(), OrderStatus.READY);

        // Update status and timestamp
        order.setStatus(OrderStatus.READY);
        order.setReadyAt(LocalDateTime.now());

        // Add status history
        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(OrderStatus.READY)
                .changedBy("KITCHEN")
                .notes(notes != null ? notes : "Order is ready for " +
                       (order.getOrderType().equals("PICKUP") ? "pickup" : "delivery"))
                .build();
        order.addStatusHistory(history);

        order = orderRepository.save(order);

        // Broadcast WebSocket event
        try {
            orderEventBroadcaster.broadcastOrderReady(order);
        } catch (Exception e) {
            log.error("Failed to broadcast order ready event: {}", e.getMessage());
        }

        // Send SMS notification
        try {
            notificationService.notifyOrderReady(order);
        } catch (Exception e) {
            log.error("Failed to send order ready SMS: {}", e.getMessage());
        }

        log.info("Order {} marked as ready", order.getOrderNumber());
        return order;
    }

    /**
     * Mark order as picked up - Courier picked up the order
     * Status: READY → PICKED_UP
     */
    @Transactional
    public Order markOrderPickedUp(Long orderId, String notes) {
        log.info("Marking order as picked up: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Validate transition
        statusTransitionValidator.validateTransition(order.getStatus(), OrderStatus.PICKED_UP);

        // Update status and timestamp
        order.setStatus(OrderStatus.PICKED_UP);
        order.setPickedUpAt(LocalDateTime.now());

        // Add status history
        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(OrderStatus.PICKED_UP)
                .changedBy("COURIER")
                .notes(notes != null ? notes : "Order picked up by courier")
                .build();
        order.addStatusHistory(history);

        order = orderRepository.save(order);

        // Broadcast WebSocket event
        try {
            orderEventBroadcaster.broadcastOrderPickedUp(order);
        } catch (Exception e) {
            log.error("Failed to broadcast order picked up event: {}", e.getMessage());
        }

        log.info("Order {} marked as picked up", order.getOrderNumber());
        return order;
    }

    /**
     * Mark order as completed - Final status
     * Status: PICKED_UP → COMPLETED
     */
    @Transactional
    public Order markOrderCompleted(Long orderId, String notes) {
        log.info("Marking order as completed: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Validate transition
        statusTransitionValidator.validateTransition(order.getStatus(), OrderStatus.COMPLETED);

        // Update status and timestamp
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());

        // Add status history
        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(OrderStatus.COMPLETED)
                .changedBy("COURIER")
                .notes(notes != null ? notes : "Order delivered successfully")
                .build();
        order.addStatusHistory(history);

        order = orderRepository.save(order);

        // Broadcast WebSocket event
        try {
            orderEventBroadcaster.broadcastOrderCompleted(order);
        } catch (Exception e) {
            log.error("Failed to broadcast order completed event: {}", e.getMessage());
        }

        // Send SMS notification
        try {
            notificationService.notifyOrderCompleted(order);
        } catch (Exception e) {
            log.error("Failed to send order completed SMS: {}", e.getMessage());
        }

        log.info("Order {} marked as completed", order.getOrderNumber());
        return order;
    }
}
