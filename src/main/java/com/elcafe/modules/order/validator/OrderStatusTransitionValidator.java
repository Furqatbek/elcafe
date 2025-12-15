package com.elcafe.modules.order.validator;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.exception.InvalidOrderStatusTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validator for order status transitions based on the state machine defined in the documentation.
 *
 * State Machine:
 * PENDING → PLACED → ACCEPTED → PREPARING → READY → PICKED_UP → COMPLETED
 *             ↓         ↓
 *         REJECTED   CANCELLED
 */
@Component
public class OrderStatusTransitionValidator {

    private final Map<OrderStatus, Set<OrderStatus>> allowedTransitions;

    public OrderStatusTransitionValidator() {
        this.allowedTransitions = new EnumMap<>(OrderStatus.class);
        initializeTransitions();
    }

    private void initializeTransitions() {
        // PENDING can transition to PLACED or CANCELLED
        allowedTransitions.put(OrderStatus.PENDING, EnumSet.of(
            OrderStatus.PLACED,
            OrderStatus.CANCELLED
        ));

        // PLACED can transition to ACCEPTED, REJECTED, or CANCELLED
        allowedTransitions.put(OrderStatus.PLACED, EnumSet.of(
            OrderStatus.ACCEPTED,
            OrderStatus.REJECTED,
            OrderStatus.CANCELLED
        ));

        // ACCEPTED can transition to PREPARING or CANCELLED
        allowedTransitions.put(OrderStatus.ACCEPTED, EnumSet.of(
            OrderStatus.PREPARING,
            OrderStatus.CANCELLED
        ));

        // PREPARING can transition to READY
        allowedTransitions.put(OrderStatus.PREPARING, EnumSet.of(
            OrderStatus.READY
        ));

        // READY can transition to PICKED_UP or COMPLETED (for pickup orders)
        allowedTransitions.put(OrderStatus.READY, EnumSet.of(
            OrderStatus.PICKED_UP,
            OrderStatus.COMPLETED
        ));

        // PICKED_UP can transition to COMPLETED
        allowedTransitions.put(OrderStatus.PICKED_UP, EnumSet.of(
            OrderStatus.COMPLETED
        ));

        // Terminal states - no transitions allowed
        allowedTransitions.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        allowedTransitions.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        allowedTransitions.put(OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class));

        // Legacy statuses - map to closest new equivalent
        allowedTransitions.put(OrderStatus.NEW, EnumSet.of(
            OrderStatus.ACCEPTED,
            OrderStatus.CANCELLED
        ));

        allowedTransitions.put(OrderStatus.COURIER_ASSIGNED, EnumSet.of(
            OrderStatus.PICKED_UP,
            OrderStatus.COMPLETED
        ));

        allowedTransitions.put(OrderStatus.ON_DELIVERY, EnumSet.of(
            OrderStatus.COMPLETED
        ));

        allowedTransitions.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
    }

    /**
     * Validates if a status transition is allowed.
     *
     * @param fromStatus Current order status
     * @param toStatus   Desired order status
     * @throws InvalidOrderStatusTransitionException if transition is not allowed
     */
    public void validateTransition(OrderStatus fromStatus, OrderStatus toStatus) {
        if (fromStatus == toStatus) {
            throw new InvalidOrderStatusTransitionException(
                "Order is already in status: " + toStatus
            );
        }

        Set<OrderStatus> allowed = allowedTransitions.get(fromStatus);
        if (allowed == null || !allowed.contains(toStatus)) {
            throw new InvalidOrderStatusTransitionException(
                String.format(
                    "Invalid status transition: %s → %s. Allowed transitions from %s: %s",
                    fromStatus, toStatus, fromStatus,
                    allowed != null ? allowed : "none"
                )
            );
        }
    }

    /**
     * Checks if a status transition is allowed without throwing an exception.
     *
     * @param fromStatus Current order status
     * @param toStatus   Desired order status
     * @return true if transition is allowed, false otherwise
     */
    public boolean isTransitionAllowed(OrderStatus fromStatus, OrderStatus toStatus) {
        if (fromStatus == toStatus) {
            return false;
        }

        Set<OrderStatus> allowed = allowedTransitions.get(fromStatus);
        return allowed != null && allowed.contains(toStatus);
    }

    /**
     * Gets all allowed transitions from a given status.
     *
     * @param fromStatus Current order status
     * @return Set of allowed next statuses
     */
    public Set<OrderStatus> getAllowedTransitions(OrderStatus fromStatus) {
        return allowedTransitions.getOrDefault(fromStatus, EnumSet.noneOf(OrderStatus.class));
    }

    /**
     * Checks if an order status is terminal (no further transitions possible).
     *
     * @param status Order status to check
     * @return true if status is terminal
     */
    public boolean isTerminalStatus(OrderStatus status) {
        Set<OrderStatus> allowed = allowedTransitions.get(status);
        return allowed == null || allowed.isEmpty();
    }

    /**
     * Checks if an order can be cancelled from the given status.
     *
     * @param status Current order status
     * @return true if order can be cancelled from this status
     */
    public boolean canBeCancelled(OrderStatus status) {
        Set<OrderStatus> allowed = allowedTransitions.get(status);
        return allowed != null && allowed.contains(OrderStatus.CANCELLED);
    }
}
