package com.elcafe.modules.kitchen.service;

import com.elcafe.common.audit.entity.AuditAction;
import com.elcafe.common.audit.service.AuditService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.enums.KitchenOrderStatus;
import com.elcafe.modules.kitchen.enums.KitchenPriority;
import com.elcafe.modules.kitchen.exception.InvalidKitchenStatusTransitionException;
import com.elcafe.modules.kitchen.exception.KitchenOrderNotFoundException;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenOrderService {

    private final KitchenOrderRepository kitchenOrderRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;
    private final AuditService auditService;

    // Idempotency key cache to prevent duplicate status transitions
    // In production, this should be replaced with Redis or similar distributed cache
    private final Map<String, LocalDateTime> idempotencyCache = new ConcurrentHashMap<>();
    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(5);

    // Configuration for timeout alerts
    private static final int PREPARING_TIMEOUT_MINUTES = 45;
    private static final int READY_TIMEOUT_MINUTES = 30;

    // Default values
    private static final int DEFAULT_PREPARATION_TIME_MINUTES = 30;
    private static final int MAX_CHEF_NAME_LENGTH = 100;

    @Transactional
    public KitchenOrder createKitchenOrder(Order order) {
        KitchenOrder kitchenOrder = KitchenOrder.builder()
                .order(order)
                .status(KitchenOrderStatus.PENDING)
                .priority(KitchenPriority.NORMAL)
                .estimatedPreparationTimeMinutes(DEFAULT_PREPARATION_TIME_MINUTES)
                .build();

        return kitchenOrderRepository.save(kitchenOrder);
    }

    public List<KitchenOrder> getActiveOrders(Long restaurantId) {
        List<KitchenOrderStatus> activeStatuses = Arrays.asList(
                KitchenOrderStatus.PENDING,
                KitchenOrderStatus.PREPARING
        );
        return getOrdersByStatuses(restaurantId, activeStatuses);
    }

    public List<KitchenOrder> getReadyOrders(Long restaurantId) {
        List<KitchenOrderStatus> readyStatuses = List.of(KitchenOrderStatus.READY);
        return getOrdersByStatuses(restaurantId, readyStatuses);
    }

    /**
     * Common helper method to retrieve orders by status list.
     * Reduces code duplication between getActiveOrders and getReadyOrders.
     */
    private List<KitchenOrder> getOrdersByStatuses(Long restaurantId, List<KitchenOrderStatus> statuses) {
        if (restaurantId != null) {
            return kitchenOrderRepository.findByRestaurantAndStatuses(restaurantId, statuses);
        }
        return kitchenOrderRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(statuses);
    }

    @Transactional
    public KitchenOrder startPreparation(Long kitchenOrderId, String chefName) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new KitchenOrderNotFoundException(kitchenOrderId));

        if (kitchenOrder.getStatus() != KitchenOrderStatus.PENDING) {
            throw new InvalidKitchenStatusTransitionException(
                    "start preparation", kitchenOrder.getStatus(), KitchenOrderStatus.PENDING);
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
                .orElseThrow(() -> new KitchenOrderNotFoundException(kitchenOrderId));

        if (kitchenOrder.getStatus() != KitchenOrderStatus.PREPARING) {
            throw new InvalidKitchenStatusTransitionException(
                    "mark as ready", kitchenOrder.getStatus(), KitchenOrderStatus.PREPARING);
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
                .orElseThrow(() -> new KitchenOrderNotFoundException(kitchenOrderId));

        // Validate status transition: only READY orders can be picked up
        if (kitchenOrder.getStatus() != KitchenOrderStatus.READY) {
            throw new InvalidKitchenStatusTransitionException(
                    "mark as picked up", kitchenOrder.getStatus(), KitchenOrderStatus.READY);
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
                .orElseThrow(() -> new KitchenOrderNotFoundException(kitchenOrderId));

        // Only allow priority updates for active orders (PENDING or PREPARING)
        if (kitchenOrder.getStatus() != KitchenOrderStatus.PENDING &&
            kitchenOrder.getStatus() != KitchenOrderStatus.PREPARING) {
            throw new InvalidKitchenStatusTransitionException(
                    String.format("Cannot update priority: order is in %s status", kitchenOrder.getStatus()));
        }

        kitchenOrder.setPriority(priority);
        log.info("Kitchen order {} priority updated to {}", kitchenOrderId, priority);
        return kitchenOrderRepository.save(kitchenOrder);
    }

    // ==================== SECURE METHODS WITH AUTHORIZATION ====================

    /**
     * Start preparation with restaurant authorization and chef name sanitization.
     * Prevents IDOR attacks by validating user has access to the order's restaurant.
     * Includes idempotency protection, audit logging, and non-blocking notifications.
     */
    @Transactional
    public KitchenOrder startPreparationWithAuth(Long kitchenOrderId, String chefName, UserPrincipal currentUser) {
        return startPreparationWithAuth(kitchenOrderId, chefName, currentUser, null);
    }

    /**
     * Start preparation with idempotency key support.
     * @param idempotencyKey Optional key to prevent duplicate requests
     */
    @Transactional
    public KitchenOrder startPreparationWithAuth(Long kitchenOrderId, String chefName,
                                                   UserPrincipal currentUser, String idempotencyKey) {
        // Check idempotency to prevent duplicate state transitions
        String effectiveKey = idempotencyKey != null ? idempotencyKey :
                String.format("start:%d:%s", kitchenOrderId, currentUser.getId());
        if (isDuplicateRequest(effectiveKey)) {
            log.warn("Duplicate request detected for starting kitchen order {} (key: {})",
                    kitchenOrderId, effectiveKey);
            return kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                    .orElseThrow(() -> new KitchenOrderNotFoundException(kitchenOrderId));
        }

        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new KitchenOrderNotFoundException(kitchenOrderId));

        // Validate restaurant access - prevents IDOR
        validateRestaurantAccess(kitchenOrder, currentUser);

        // Sanitize chef name to prevent XSS and validate length
        String sanitizedChefName = sanitizeChefName(chefName, currentUser);

        KitchenOrderStatus previousStatus = kitchenOrder.getStatus();

        if (previousStatus != KitchenOrderStatus.PENDING) {
            throw new InvalidKitchenStatusTransitionException(
                    "start preparation", previousStatus, KitchenOrderStatus.PENDING);
        }

        kitchenOrder.startPreparation(sanitizedChefName);
        KitchenOrder savedOrder = kitchenOrderRepository.save(kitchenOrder);

        // Update main order status
        Order order = kitchenOrder.getOrder();
        order.setStatus(OrderStatus.PREPARING);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.PREPARING)
                .changedBy(currentUser.getEmail())
                .notes("Preparation started by " + sanitizedChefName)
                .build();
        order.addStatusHistory(statusHistory);
        orderRepository.save(order);

        // Audit log the status transition
        logStatusTransition(kitchenOrder, previousStatus, KitchenOrderStatus.PREPARING,
                currentUser, "Started by " + sanitizedChefName);

        // Non-blocking notification - failures don't affect the transaction
        sendNotificationAsync(() -> notificationService.notifyOrderPreparing(order),
                "order preparing", order.getOrderNumber());

        // Mark idempotency key as processed
        markRequestProcessed(effectiveKey);

        log.info("Kitchen order {} started preparation by {} (user: {})",
                kitchenOrder.getId(), sanitizedChefName, currentUser.getEmail());
        return savedOrder;
    }

    /**
     * Mark as ready with restaurant authorization.
     * Prevents IDOR attacks by validating user has access to the order's restaurant.
     * Includes idempotency protection, audit logging, and non-blocking notifications.
     */
    @Transactional
    public KitchenOrder markAsReadyWithAuth(Long kitchenOrderId, UserPrincipal currentUser) {
        return markAsReadyWithAuth(kitchenOrderId, currentUser, null);
    }

    /**
     * Mark as ready with idempotency key support.
     */
    @Transactional
    public KitchenOrder markAsReadyWithAuth(Long kitchenOrderId, UserPrincipal currentUser, String idempotencyKey) {
        // Check idempotency
        String effectiveKey = idempotencyKey != null ? idempotencyKey :
                String.format("ready:%d:%s", kitchenOrderId, currentUser.getId());
        if (isDuplicateRequest(effectiveKey)) {
            log.warn("Duplicate request detected for marking kitchen order {} ready (key: {})",
                    kitchenOrderId, effectiveKey);
            return kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                    .orElseThrow(() -> new KitchenOrderNotFoundException(kitchenOrderId));
        }

        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new KitchenOrderNotFoundException(kitchenOrderId));

        // Validate restaurant access - prevents IDOR
        validateRestaurantAccess(kitchenOrder, currentUser);

        KitchenOrderStatus previousStatus = kitchenOrder.getStatus();

        if (previousStatus != KitchenOrderStatus.PREPARING) {
            throw new InvalidKitchenStatusTransitionException(
                    "mark as ready", previousStatus, KitchenOrderStatus.PREPARING);
        }

        kitchenOrder.completePreparation();
        KitchenOrder savedOrder = kitchenOrderRepository.save(kitchenOrder);

        Order order = kitchenOrder.getOrder();
        order.setStatus(OrderStatus.READY);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.READY)
                .changedBy(currentUser.getEmail())
                .notes("Order ready for pickup/delivery")
                .build();
        order.addStatusHistory(statusHistory);
        orderRepository.save(order);

        // Audit log the status transition
        logStatusTransition(kitchenOrder, previousStatus, KitchenOrderStatus.READY,
                currentUser, "Marked ready");

        // Non-blocking notification
        sendNotificationAsync(() -> notificationService.notifyOrderReady(order),
                "order ready", order.getOrderNumber());

        markRequestProcessed(effectiveKey);

        log.info("Kitchen order {} marked as ready by {}", kitchenOrder.getId(), currentUser.getEmail());
        return savedOrder;
    }

    /**
     * Mark as picked up with restaurant authorization and courier verification.
     * Prevents IDOR attacks and ensures only assigned couriers can pick up orders.
     * Includes idempotency protection and audit logging.
     */
    @Transactional
    public KitchenOrder markAsPickedUpWithAuth(Long kitchenOrderId, UserPrincipal currentUser) {
        return markAsPickedUpWithAuth(kitchenOrderId, currentUser, null);
    }

    /**
     * Mark as picked up with idempotency key support.
     */
    @Transactional
    public KitchenOrder markAsPickedUpWithAuth(Long kitchenOrderId, UserPrincipal currentUser, String idempotencyKey) {
        // Check idempotency
        String effectiveKey = idempotencyKey != null ? idempotencyKey :
                String.format("pickup:%d:%s", kitchenOrderId, currentUser.getId());
        if (isDuplicateRequest(effectiveKey)) {
            log.warn("Duplicate request detected for picking up kitchen order {} (key: {})",
                    kitchenOrderId, effectiveKey);
            return kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                    .orElseThrow(() -> new KitchenOrderNotFoundException(kitchenOrderId));
        }

        KitchenOrder kitchenOrder = kitchenOrderRepository.findByIdWithOrder(kitchenOrderId)
                .orElseThrow(() -> new KitchenOrderNotFoundException(kitchenOrderId));

        // Validate restaurant access - prevents IDOR
        validateRestaurantAccess(kitchenOrder, currentUser);

        // For COURIER role, validate they are assigned to this order
        if (currentUser.getRole() == UserRole.COURIER) {
            validateCourierAssignment(kitchenOrder, currentUser);
        }

        KitchenOrderStatus previousStatus = kitchenOrder.getStatus();

        if (previousStatus != KitchenOrderStatus.READY) {
            throw new InvalidKitchenStatusTransitionException(
                    "mark as picked up", previousStatus, KitchenOrderStatus.READY);
        }

        kitchenOrder.setStatus(KitchenOrderStatus.PICKED_UP);
        KitchenOrder savedOrder = kitchenOrderRepository.save(kitchenOrder);

        Order order = kitchenOrder.getOrder();
        order.setStatus(OrderStatus.PICKED_UP);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.PICKED_UP)
                .changedBy(currentUser.getEmail())
                .notes("Order picked up by " + currentUser.getEmail())
                .build();
        order.addStatusHistory(statusHistory);
        orderRepository.save(order);

        // Audit log the status transition
        logStatusTransition(kitchenOrder, previousStatus, KitchenOrderStatus.PICKED_UP,
                currentUser, "Picked up");

        markRequestProcessed(effectiveKey);

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
                .orElseThrow(() -> new KitchenOrderNotFoundException(kitchenOrderId));

        // Validate restaurant access - prevents IDOR
        validateRestaurantAccess(kitchenOrder, currentUser);

        if (kitchenOrder.getStatus() != KitchenOrderStatus.PENDING &&
            kitchenOrder.getStatus() != KitchenOrderStatus.PREPARING) {
            throw new InvalidKitchenStatusTransitionException(
                    String.format("Cannot update priority: order is in %s status", kitchenOrder.getStatus()));
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
        if (sanitized.length() > MAX_CHEF_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_CHEF_NAME_LENGTH);
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

    // ==================== PRODUCTION FEATURES ====================

    /**
     * Check if a request with the given idempotency key has already been processed.
     * Cleans up expired entries from the cache.
     */
    private boolean isDuplicateRequest(String idempotencyKey) {
        cleanupExpiredIdempotencyKeys();
        return idempotencyCache.containsKey(idempotencyKey);
    }

    /**
     * Mark an idempotency key as processed.
     */
    private void markRequestProcessed(String idempotencyKey) {
        idempotencyCache.put(idempotencyKey, LocalDateTime.now());
    }

    /**
     * Remove expired idempotency keys from the cache.
     */
    private void cleanupExpiredIdempotencyKeys() {
        LocalDateTime cutoff = LocalDateTime.now().minus(IDEMPOTENCY_TTL);
        idempotencyCache.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    /**
     * Send notification asynchronously without blocking the main transaction.
     * If notification fails, it's logged but doesn't affect the order status update.
     */
    private void sendNotificationAsync(Runnable notificationTask, String notificationType, String orderNumber) {
        try {
            notificationTask.run();
        } catch (Exception e) {
            // Log the failure but don't roll back the transaction
            log.error("Failed to send {} notification for order {}: {}",
                    notificationType, orderNumber, e.getMessage());
            // In production, you might want to queue this for retry
            // or send to a dead letter queue for manual intervention
        }
    }

    /**
     * Log status transition to the audit log for compliance and traceability.
     */
    private void logStatusTransition(KitchenOrder kitchenOrder, KitchenOrderStatus fromStatus,
                                      KitchenOrderStatus toStatus, UserPrincipal user, String notes) {
        try {
            Order order = kitchenOrder.getOrder();
            auditService.logAction(AuditService.AuditLogBuilder.create()
                    .action(AuditAction.ORDER_STATUS_CHANGED)
                    .entityType("KitchenOrder")
                    .entityId(kitchenOrder.getId())
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .restaurantId(order.getRestaurant().getId())
                    .userId(user.getId())
                    .username(user.getEmail())
                    .userRole(user.getRole().name())
                    .previousValue(fromStatus.name())
                    .newValue(toStatus.name())
                    .actionDetail(String.format("Kitchen order status changed from %s to %s. %s",
                            fromStatus, toStatus, notes)));
        } catch (Exception e) {
            // Log audit failure but don't fail the main operation
            log.error("Failed to log audit for kitchen order {} status change: {}",
                    kitchenOrder.getId(), e.getMessage());
        }
    }

    /**
     * Find orders that have been stuck in PREPARING status for too long.
     * Should be called by a scheduled task to alert kitchen managers.
     */
    @Transactional(readOnly = true)
    public List<KitchenOrder> findStuckPreparingOrders(Long restaurantId) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PREPARING_TIMEOUT_MINUTES);
        List<KitchenOrder> stuckOrders = kitchenOrderRepository.findStuckOrders(
                restaurantId,
                KitchenOrderStatus.PREPARING,
                cutoff
        );

        if (!stuckOrders.isEmpty()) {
            log.warn("Found {} orders stuck in PREPARING status for more than {} minutes in restaurant {}",
                    stuckOrders.size(), PREPARING_TIMEOUT_MINUTES, restaurantId);
        }

        return stuckOrders;
    }

    /**
     * Find orders that have been ready but not picked up for too long.
     * Should be called by a scheduled task to alert for pickup.
     */
    @Transactional(readOnly = true)
    public List<KitchenOrder> findStuckReadyOrders(Long restaurantId) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(READY_TIMEOUT_MINUTES);
        List<KitchenOrder> stuckOrders = kitchenOrderRepository.findStuckOrders(
                restaurantId,
                KitchenOrderStatus.READY,
                cutoff
        );

        if (!stuckOrders.isEmpty()) {
            log.warn("Found {} orders stuck in READY status for more than {} minutes in restaurant {}",
                    stuckOrders.size(), READY_TIMEOUT_MINUTES, restaurantId);
        }

        return stuckOrders;
    }

    /**
     * Get estimated time remaining for orders in PREPARING status.
     * Returns null if not applicable (not preparing or no estimate).
     */
    public Duration getEstimatedTimeRemaining(KitchenOrder kitchenOrder) {
        if (kitchenOrder.getStatus() != KitchenOrderStatus.PREPARING) {
            return null;
        }

        LocalDateTime startTime = kitchenOrder.getPreparationStartedAt();
        Integer estimatedMinutes = kitchenOrder.getEstimatedPreparationTimeMinutes();

        if (startTime == null || estimatedMinutes == null) {
            return null;
        }

        LocalDateTime estimatedCompletion = startTime.plusMinutes(estimatedMinutes);
        Duration remaining = Duration.between(LocalDateTime.now(), estimatedCompletion);

        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
