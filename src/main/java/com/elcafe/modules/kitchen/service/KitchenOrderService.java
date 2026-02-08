package com.elcafe.modules.kitchen.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.enums.KitchenOrderStatus;
import com.elcafe.modules.kitchen.enums.KitchenPriority;
import com.elcafe.modules.kitchen.repository.KitchenOrderRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.entity.DeliveryInfo;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderStatusHistory;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenOrderService {

    private final KitchenOrderRepository kitchenOrderRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @Transactional
    public KitchenOrder createKitchenOrder(Order order) {
        KitchenOrder kitchenOrder = KitchenOrder.builder()
                .order(order)
                .status(KitchenOrderStatus.PENDING)
                .priority(KitchenPriority.NORMAL)
                .estimatedPreparationTimeMinutes(30) // Default 30 minutes
                .build();

        return kitchenOrderRepository.save(kitchenOrder);
    }

    public List<KitchenOrder> getActiveOrders(Long restaurantId) {
        List<KitchenOrderStatus> activeStatuses = Arrays.asList(
                KitchenOrderStatus.PENDING,
                KitchenOrderStatus.PREPARING
        );

        if (restaurantId != null) {
            return kitchenOrderRepository.findByRestaurantAndStatuses(restaurantId, activeStatuses);
        }
        return kitchenOrderRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(activeStatuses);
    }

    public List<KitchenOrder> getReadyOrders(Long restaurantId) {
        List<KitchenOrderStatus> readyStatuses = List.of(KitchenOrderStatus.READY);

        if (restaurantId != null) {
            return kitchenOrderRepository.findByRestaurantAndStatuses(restaurantId, readyStatuses);
        }
        return kitchenOrderRepository.findByStatusOrderByCreatedAtAsc(KitchenOrderStatus.READY);
    }

    @Transactional
    public KitchenOrder startPreparation(Long kitchenOrderId, String chefName) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        if (kitchenOrder.getStatus() != KitchenOrderStatus.PENDING) {
            throw new IllegalStateException(
                    String.format("Cannot start preparation: current status is %s, expected PENDING",
                            kitchenOrder.getStatus()));
        }

        kitchenOrder.startPreparation(chefName);
        KitchenOrder savedOrder = kitchenOrderRepository.save(kitchenOrder);

        // Update main order status
        Order order = kitchenOrder.getOrder();
        order.setStatus(OrderStatus.PREPARING);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.PREPARING)
                .changedBy(chefName)
                .notes("Preparation started by " + chefName)
                .build();
        order.addStatusHistory(statusHistory);
        orderRepository.save(order);

        // Notify
        notificationService.notifyOrderPreparing(order);

        log.info("Kitchen order {} started preparation by {}", kitchenOrder.getId(), chefName);
        return savedOrder;
    }

    @Transactional
    public KitchenOrder markAsReady(Long kitchenOrderId) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        if (kitchenOrder.getStatus() != KitchenOrderStatus.PREPARING) {
            throw new IllegalStateException(
                    String.format("Cannot mark as ready: current status is %s, expected PREPARING",
                            kitchenOrder.getStatus()));
        }

        kitchenOrder.completePreparation();
        KitchenOrder savedOrder = kitchenOrderRepository.save(kitchenOrder);

        // Update main order status
        Order order = kitchenOrder.getOrder();
        order.setStatus(OrderStatus.READY);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.READY)
                .changedBy("KITCHEN")
                .notes("Order ready for pickup/delivery")
                .build();
        order.addStatusHistory(statusHistory);
        orderRepository.save(order);

        // Notify couriers and customer
        notificationService.notifyOrderReady(order);

        log.info("Kitchen order {} marked as ready", kitchenOrder.getId());
        return savedOrder;
    }

    @Transactional
    public KitchenOrder markAsPickedUp(Long kitchenOrderId) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        // Validate status transition: only READY orders can be picked up
        if (kitchenOrder.getStatus() != KitchenOrderStatus.READY) {
            throw new IllegalStateException(
                    String.format("Cannot mark order as picked up: current status is %s, expected READY",
                            kitchenOrder.getStatus()));
        }

        kitchenOrder.setStatus(KitchenOrderStatus.PICKED_UP);
        KitchenOrder savedOrder = kitchenOrderRepository.save(kitchenOrder);

        // Update main order status
        Order order = kitchenOrder.getOrder();
        order.setStatus(OrderStatus.PICKED_UP);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.PICKED_UP)
                .changedBy("COURIER")
                .notes("Order picked up from kitchen")
                .build();
        order.addStatusHistory(statusHistory);
        orderRepository.save(order);

        log.info("Kitchen order {} marked as picked up", kitchenOrder.getId());
        return savedOrder;
    }

    @Transactional
    public KitchenOrder updatePriority(Long kitchenOrderId, KitchenPriority priority) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        // Only allow priority updates for active orders (PENDING or PREPARING)
        if (kitchenOrder.getStatus() != KitchenOrderStatus.PENDING &&
            kitchenOrder.getStatus() != KitchenOrderStatus.PREPARING) {
            throw new IllegalStateException(
                    String.format("Cannot update priority: order is in %s status",
                            kitchenOrder.getStatus()));
        }

        kitchenOrder.setPriority(priority);
        log.info("Kitchen order {} priority updated to {}", kitchenOrderId, priority);
        return kitchenOrderRepository.save(kitchenOrder);
    }

    // ==================== SECURE METHODS WITH AUTHORIZATION ====================

    /**
     * Start preparation with restaurant authorization and chef name sanitization.
     * Prevents IDOR attacks by validating user has access to the order's restaurant.
     */
    @Transactional
    public KitchenOrder startPreparationWithAuth(Long kitchenOrderId, String chefName, UserPrincipal currentUser) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        // Validate restaurant access - prevents IDOR
        validateRestaurantAccess(kitchenOrder, currentUser);

        // Sanitize chef name to prevent XSS and validate length
        String sanitizedChefName = sanitizeChefName(chefName, currentUser);

        if (kitchenOrder.getStatus() != KitchenOrderStatus.PENDING) {
            throw new IllegalStateException(
                    String.format("Cannot start preparation: current status is %s, expected PENDING",
                            kitchenOrder.getStatus()));
        }

        kitchenOrder.startPreparation(sanitizedChefName);
        KitchenOrder savedOrder = kitchenOrderRepository.save(kitchenOrder);

        // Update main order status
        Order order = kitchenOrder.getOrder();
        order.setStatus(OrderStatus.PREPARING);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.PREPARING)
                .changedBy(currentUser.getEmail()) // Use authenticated user's email for audit
                .notes("Preparation started by " + sanitizedChefName)
                .build();
        order.addStatusHistory(statusHistory);
        orderRepository.save(order);

        notificationService.notifyOrderPreparing(order);

        log.info("Kitchen order {} started preparation by {} (user: {})",
                kitchenOrder.getId(), sanitizedChefName, currentUser.getEmail());
        return savedOrder;
    }

    /**
     * Mark as ready with restaurant authorization.
     * Prevents IDOR attacks by validating user has access to the order's restaurant.
     */
    @Transactional
    public KitchenOrder markAsReadyWithAuth(Long kitchenOrderId, UserPrincipal currentUser) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        // Validate restaurant access - prevents IDOR
        validateRestaurantAccess(kitchenOrder, currentUser);

        if (kitchenOrder.getStatus() != KitchenOrderStatus.PREPARING) {
            throw new IllegalStateException(
                    String.format("Cannot mark as ready: current status is %s, expected PREPARING",
                            kitchenOrder.getStatus()));
        }

        kitchenOrder.completePreparation();
        KitchenOrder savedOrder = kitchenOrderRepository.save(kitchenOrder);

        Order order = kitchenOrder.getOrder();
        order.setStatus(OrderStatus.READY);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.READY)
                .changedBy(currentUser.getEmail()) // Use authenticated user's email
                .notes("Order ready for pickup/delivery")
                .build();
        order.addStatusHistory(statusHistory);
        orderRepository.save(order);

        notificationService.notifyOrderReady(order);

        log.info("Kitchen order {} marked as ready by {}", kitchenOrder.getId(), currentUser.getEmail());
        return savedOrder;
    }

    /**
     * Mark as picked up with restaurant authorization and courier verification.
     * Prevents IDOR attacks and ensures only assigned couriers can pick up orders.
     */
    @Transactional
    public KitchenOrder markAsPickedUpWithAuth(Long kitchenOrderId, UserPrincipal currentUser) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        // Validate restaurant access - prevents IDOR
        validateRestaurantAccess(kitchenOrder, currentUser);

        // For COURIER role, validate they are assigned to this order
        if (currentUser.getRole() == UserRole.COURIER) {
            validateCourierAssignment(kitchenOrder, currentUser);
        }

        if (kitchenOrder.getStatus() != KitchenOrderStatus.READY) {
            throw new IllegalStateException(
                    String.format("Cannot mark order as picked up: current status is %s, expected READY",
                            kitchenOrder.getStatus()));
        }

        kitchenOrder.setStatus(KitchenOrderStatus.PICKED_UP);
        KitchenOrder savedOrder = kitchenOrderRepository.save(kitchenOrder);

        Order order = kitchenOrder.getOrder();
        order.setStatus(OrderStatus.PICKED_UP);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.PICKED_UP)
                .changedBy(currentUser.getEmail()) // Use authenticated user's email
                .notes("Order picked up by " + currentUser.getEmail())
                .build();
        order.addStatusHistory(statusHistory);
        orderRepository.save(order);

        log.info("Kitchen order {} marked as picked up by {}", kitchenOrder.getId(), currentUser.getEmail());
        return savedOrder;
    }

    /**
     * Update priority with restaurant authorization.
     * Prevents IDOR attacks by validating user has access to the order's restaurant.
     */
    @Transactional
    public KitchenOrder updatePriorityWithAuth(Long kitchenOrderId, KitchenPriority priority, UserPrincipal currentUser) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        // Validate restaurant access - prevents IDOR
        validateRestaurantAccess(kitchenOrder, currentUser);

        if (kitchenOrder.getStatus() != KitchenOrderStatus.PENDING &&
            kitchenOrder.getStatus() != KitchenOrderStatus.PREPARING) {
            throw new IllegalStateException(
                    String.format("Cannot update priority: order is in %s status",
                            kitchenOrder.getStatus()));
        }

        kitchenOrder.setPriority(priority);
        log.info("Kitchen order {} priority updated to {} by {}", kitchenOrderId, priority, currentUser.getEmail());
        return kitchenOrderRepository.save(kitchenOrder);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Validates that the current user has access to the kitchen order's restaurant.
     * ADMIN users have access to all restaurants.
     * Other users must belong to the same restaurant as the order.
     */
    private void validateRestaurantAccess(KitchenOrder kitchenOrder, UserPrincipal currentUser) {
        Order order = kitchenOrder.getOrder();
        if (order == null || order.getRestaurant() == null) {
            throw new IllegalStateException("Order or restaurant not found for kitchen order");
        }

        Long orderRestaurantId = order.getRestaurant().getId();
        restaurantAuthorizationService.validateRestaurantAccess(orderRestaurantId);
    }

    /**
     * Validates that a courier is assigned to the order.
     * For delivery orders, checks if the courier is assigned in the DeliveryInfo.
     * For dine-in orders, allows any authenticated courier from the same restaurant.
     */
    private void validateCourierAssignment(KitchenOrder kitchenOrder, UserPrincipal currentUser) {
        Order order = kitchenOrder.getOrder();
        DeliveryInfo deliveryInfo = order.getDeliveryInfo();

        // For orders with delivery info, validate courier assignment
        if (deliveryInfo != null && deliveryInfo.getCourierId() != null) {
            if (!deliveryInfo.getCourierId().equals(currentUser.getId())) {
                log.warn("Courier {} attempted to pick up order {} assigned to courier {}",
                        currentUser.getId(), order.getId(), deliveryInfo.getCourierId());
                throw new AccessDeniedException("You are not assigned to this delivery order");
            }
        }
        // For orders without specific courier assignment (dine-in/pickup),
        // restaurant access validation is sufficient
    }

    /**
     * Sanitizes the chef name parameter to prevent XSS attacks.
     * Also validates the name is reasonable and uses authenticated user's email if name is suspicious.
     */
    private String sanitizeChefName(String chefName, UserPrincipal currentUser) {
        if (chefName == null || chefName.isBlank()) {
            // Use authenticated user's email if no chef name provided
            return currentUser.getEmail();
        }

        // Trim and limit length
        String sanitized = chefName.trim();
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }

        // HTML escape to prevent XSS
        sanitized = HtmlUtils.htmlEscape(sanitized);

        // Remove any potentially dangerous characters (only allow alphanumeric, spaces, and basic punctuation)
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\s\\-_.,']", "");

        if (sanitized.isBlank()) {
            return currentUser.getEmail();
        }

        return sanitized;
    }
}
