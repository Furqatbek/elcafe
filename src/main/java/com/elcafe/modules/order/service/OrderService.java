package com.elcafe.modules.order.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.financial.service.RevenueService;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.inventory.service.InventoryValuationService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderStatusHistory;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import com.elcafe.modules.settings.service.PrintService;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.PaymentRepository;
import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.notification.service.CustomerNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elcafe.modules.order.specification.OrderSpecification;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final DailyOrderSequenceService dailyOrderSequenceService;
    private final RestaurantTableRepository restaurantTableRepository;
    private final ShiftTimeService shiftTimeService;
    private final PaymentRepository paymentRepository;
    @Lazy private final RevenueService revenueService;
    @Lazy private final PrintService printService;
    @Lazy private final InventoryValuationService inventoryValuationService;
    @Lazy private final OwnerNotificationService ownerNotificationService;
    @Lazy private final CustomerNotificationService customerNotificationService;

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

        // Print kitchen order if enabled
        try {
            printService.printKitchenOrder(order);
        } catch (Exception e) {
            log.error("Failed to print kitchen order, but order was created successfully", e);
            // Don't fail order creation if printing fails
        }

        // Notify owners/staff via Telegram
        try {
            if (ownerNotificationService != null) {
                ownerNotificationService.notifyNewOrder(order);
            }
        } catch (Exception e) {
            log.error("Failed to send owner notification for order, but order was created successfully", e);
            // Don't fail order creation if notification fails
        }

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

        // Check inventory availability and deduct stock when order is accepted
        if (newStatus == OrderStatus.ACCEPTED) {
            log.info("Checking ingredient availability for order {}", order.getOrderNumber());

            if (!inventoryService.checkIngredientAvailability(order)) {
                List<String> missingIngredients = new ArrayList<>();
                for (var item : order.getItems()) {
                    missingIngredients.addAll(
                        inventoryService.getMissingIngredients(item.getProductId(), item.getQuantity())
                    );
                }

                String errorMsg = "Insufficient ingredients for order: " + String.join(", ", missingIngredients);
                log.error(errorMsg);
                throw new BadRequestException(errorMsg);
            }

            // Deduct ingredients from stock
            try {
                inventoryService.deductIngredientsForOrder(order);
                log.info("Successfully deducted ingredients for order {}", order.getOrderNumber());
            } catch (Exception e) {
                log.error("Failed to deduct ingredients for order {}: {}", order.getOrderNumber(), e.getMessage());
                throw new BadRequestException("Failed to process inventory: " + e.getMessage());
            }
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

        // Record revenue when order is completed or delivered
        if (newStatus == OrderStatus.COMPLETED || newStatus == OrderStatus.DELIVERED) {
            if (revenueService != null) {
                try {
                    revenueService.recordOrderRevenue(order);
                    log.info("Revenue recorded for completed order: {}", order.getOrderNumber());
                } catch (Exception e) {
                    log.error("Failed to record revenue for order {}: {}", order.getOrderNumber(), e.getMessage());
                    // Don't fail the order status update if revenue recording fails
                }
            }
        }

        // Release tables when dine-in order is completed, delivered, or cancelled
        // Use the new hasTables() helper method
        boolean isDineInOrder = order.getOrderType() == OrderType.DINE_IN || order.hasTables();
        if (isDineInOrder &&
                (newStatus == OrderStatus.COMPLETED || newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.CANCELLED)) {
            releaseOrderTables(order);
        }

        // Restore inventory when order is cancelled (only if it was previously accepted/deducted)
        if (newStatus == OrderStatus.CANCELLED && inventoryValuationService != null) {
            try {
                inventoryValuationService.restoreInventoryForOrder(order.getId());
                log.info("Inventory restored for cancelled order: {}", order.getOrderNumber());
            } catch (Exception e) {
                log.error("Failed to restore inventory for cancelled order {}: {}", order.getOrderNumber(), e.getMessage());
                // Don't fail the order cancellation if inventory restoration fails
            }
        }

        // Notify owners/staff about cancelled order
        if (newStatus == OrderStatus.CANCELLED && ownerNotificationService != null) {
            try {
                ownerNotificationService.notifyOrderCancelled(order, notes);
            } catch (Exception e) {
                log.error("Failed to send cancellation notification for order {}: {}", order.getOrderNumber(), e.getMessage());
            }
        }

        // Notify customer about order status change via Telegram
        if (customerNotificationService != null && shouldNotifyCustomer(newStatus)) {
            try {
                customerNotificationService.notifyOrderStatusUpdate(order, newStatus);
            } catch (Exception e) {
                log.error("Failed to send customer notification for order {}: {}", order.getOrderNumber(), e.getMessage());
            }
        }

        return order;
    }

    /**
     * Determine if customer should be notified for this status change
     */
    private boolean shouldNotifyCustomer(OrderStatus status) {
        return switch (status) {
            case ACCEPTED, PREPARING, READY, ON_DELIVERY, DELIVERED, COMPLETED, CANCELLED -> true;
            default -> false;
        };
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

    /**
     * Get orders with filters for order history page.
     * Supports filtering by restaurant, status, order type, date range, and search term.
     * Date ranges are adjusted to shift boundaries based on restaurant business hours,
     * unless isShiftAware is true (dates already calculated by ShiftTimeService).
     */
    @Transactional(readOnly = true)
    public Page<Order> getOrdersWithFilters(
            Long restaurantId,
            OrderStatus status,
            OrderType orderType,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String search,
            boolean isShiftAware,
            Pageable pageable
    ) {
        log.info("Fetching orders with filters: restaurantId={}, status={}, orderType={}, fromDate={}, toDate={}, search={}, isShiftAware={}",
                restaurantId, status, orderType, fromDate, toDate, search, isShiftAware);

        // Adjust date range to shift boundaries (only if not already shift-aware)
        LocalDateTime adjustedFromDate = fromDate;
        LocalDateTime adjustedToDate = toDate;

        if (!isShiftAware && restaurantId != null && (fromDate != null || toDate != null)) {
            // Use ShiftTimeService to get proper shift boundaries
            if (fromDate != null && toDate != null) {
                ShiftTimeService.ShiftTimeRange shiftRange = shiftTimeService.getShiftTimeRangeForPeriod(
                        restaurantId, fromDate.toLocalDate(), toDate.toLocalDate()
                );
                adjustedFromDate = shiftRange.start();
                adjustedToDate = shiftRange.end();
            } else if (fromDate != null) {
                ShiftTimeService.ShiftTimeRange shiftRange = shiftTimeService.getShiftTimeRange(
                        restaurantId, fromDate.toLocalDate()
                );
                adjustedFromDate = shiftRange.start();
            } else if (toDate != null) {
                ShiftTimeService.ShiftTimeRange shiftRange = shiftTimeService.getShiftTimeRange(
                        restaurantId, toDate.toLocalDate()
                );
                adjustedToDate = shiftRange.end();
            }

            log.info("Adjusted to shift boundaries: {} to {} -> {} to {}",
                    fromDate, toDate, adjustedFromDate, adjustedToDate);
        }

        Specification<Order> spec = OrderSpecification.withFilters(
                restaurantId,
                status,
                orderType,
                adjustedFromDate,
                adjustedToDate,
                search
        );

        return orderRepository.findAll(spec, pageable);
    }

    /**
     * Get orders for a restaurant from the last 7 days using shift-aware boundaries.
     */
    @Transactional(readOnly = true)
    public List<Order> getOrdersByRestaurant(Long restaurantId) {
        // Get shift-aware boundaries for 7 days ago to now
        LocalDate today = shiftTimeService.getCurrentBusinessDay(restaurantId);
        LocalDate sevenDaysAgo = today.minusDays(7);

        ShiftTimeService.ShiftTimeRange shiftRange = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, sevenDaysAgo, today
        );

        return orderRepository.findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
                restaurantId,
                shiftRange.start(),
                shiftRange.end()
        );
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Order> getPendingOrders() {
        return orderRepository.findByStatusOrderByCreatedAtAsc(OrderStatus.NEW);
    }

    @Transactional
    public Order acceptOrder(Long orderId, String changedBy, String notes) {
        return updateOrderStatus(orderId, OrderStatus.ACCEPTED, notes, changedBy);
    }

    @Transactional
    public Order rejectOrder(Long orderId, String reason, String changedBy) {
        return updateOrderStatus(orderId, OrderStatus.CANCELLED, reason, changedBy);
    }

    @Transactional
    public Order cancelOrder(Long orderId, String reason, String changedBy) {
        return updateOrderStatus(orderId, OrderStatus.CANCELLED, reason, changedBy);
    }

    private String generateOrderNumber() {
        return dailyOrderSequenceService.generateNextOrderNumber();
    }

    private boolean isValidStatusTransition(OrderStatus current, OrderStatus next) {
        return switch (current) {
            case PENDING -> next == OrderStatus.NEW || next == OrderStatus.PLACED || next == OrderStatus.ACCEPTED || next == OrderStatus.CANCELLED;
            case NEW -> next == OrderStatus.PLACED || next == OrderStatus.ACCEPTED || next == OrderStatus.CANCELLED;
            case PLACED -> next == OrderStatus.ACCEPTED || next == OrderStatus.REJECTED || next == OrderStatus.CANCELLED;
            case ACCEPTED -> next == OrderStatus.PREPARING || next == OrderStatus.CANCELLED;
            case REJECTED -> false;
            case PREPARING -> next == OrderStatus.READY || next == OrderStatus.CANCELLED;
            case READY -> next == OrderStatus.PICKED_UP || next == OrderStatus.COURIER_ASSIGNED || next == OrderStatus.CANCELLED;
            case PICKED_UP -> next == OrderStatus.COMPLETED || next == OrderStatus.COURIER_ASSIGNED;
            case COURIER_ASSIGNED -> next == OrderStatus.ON_DELIVERY || next == OrderStatus.CANCELLED;
            case ON_DELIVERY -> next == OrderStatus.DELIVERED || next == OrderStatus.COMPLETED;
            case DELIVERED -> next == OrderStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /**
     * Release all tables associated with a dine-in order by setting status to AVAILABLE
     */
    private void releaseOrderTables(Order order) {
        try {
            // Use the new getTableIdList() helper method which handles all cases
            List<Long> tableIds = order.getTableIdList();
            for (Long tableId : tableIds) {
                restaurantTableRepository.findById(tableId).ifPresent(table -> {
                    table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
                    restaurantTableRepository.save(table);
                    log.info("Table {} released (set to AVAILABLE) for order {}", table.getTableNumber(), order.getOrderNumber());
                });
            }
        } catch (Exception e) {
            log.error("Failed to release tables for order {}: {}", order.getOrderNumber(), e.getMessage());
            // Don't fail the order status update if table release fails
        }
    }

    /**
     * Revert a closed order (DELIVERED/COMPLETED) back to active status.
     * This voids all payments and resets the order to the specified target status.
     * Used when managers mistakenly close orders that should still be active.
     *
     * @param orderId The order ID to revert
     * @param targetStatus The status to revert to (defaults to READY if null)
     * @param reason The reason for reverting the order
     * @param revertedBy Who is reverting the order
     * @return The updated order
     */
    @Transactional
    public Order revertOrderToActive(Long orderId, OrderStatus targetStatus, String reason, String revertedBy) {
        log.info("Reverting order {} to active status, requested by {}", orderId, revertedBy);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        OrderStatus currentStatus = order.getStatus();

        // Only allow reverting from closed statuses
        if (currentStatus != OrderStatus.DELIVERED && currentStatus != OrderStatus.COMPLETED) {
            throw new BadRequestException(
                    String.format("Cannot revert order with status %s. Only DELIVERED or COMPLETED orders can be reverted.", currentStatus)
            );
        }

        // Default target status is READY
        if (targetStatus == null) {
            targetStatus = OrderStatus.READY;
        }

        // Validate target status is an active status
        if (targetStatus == OrderStatus.DELIVERED || targetStatus == OrderStatus.COMPLETED ||
            targetStatus == OrderStatus.CANCELLED || targetStatus == OrderStatus.REJECTED) {
            throw new BadRequestException(
                    String.format("Cannot revert to status %s. Target must be an active status (e.g., READY, ACCEPTED, PREPARING).", targetStatus)
            );
        }

        // Void all payments associated with the order
        var payments = paymentRepository.findByOrderId(orderId);
        for (var payment : payments) {
            if (payment.getStatus() == PaymentStatus.COMPLETED ||
                payment.getStatus() == PaymentStatus.PENDING ||
                payment.getStatus() == PaymentStatus.PROCESSING) {
                payment.setStatus(PaymentStatus.VOIDED);
                payment.setRefundedAmount(payment.getTotalWithTip());
                payment.setRefundReason("Order reverted: " + reason);
                payment.setRefundedAt(java.time.OffsetDateTime.now());
                paymentRepository.save(payment);
                log.info("Voided payment {} for reverted order {}", payment.getId(), orderId);
            }
        }

        // Update order status
        order.setStatus(targetStatus);
        order.setPaymentStatus(PaymentStatus.PENDING);

        // Clear completed timestamps
        order.setCompletedAt(null);

        // Add status history
        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(targetStatus)
                .changedBy(revertedBy)
                .notes("Order reverted from " + currentStatus + ": " + reason)
                .build();
        order.addStatusHistory(history);

        // Re-occupy tables for dine-in orders
        boolean isDineInOrder = order.getOrderType() == OrderType.DINE_IN || order.hasTables();
        if (isDineInOrder) {
            reoccupyOrderTables(order);
        }

        order = orderRepository.save(order);
        log.info("Order {} reverted from {} to {}", orderId, currentStatus, targetStatus);

        return order;
    }

    /**
     * Re-occupy tables for a reverted dine-in order
     */
    private void reoccupyOrderTables(Order order) {
        try {
            // Use the new getTableIdList() helper method which handles all cases
            List<Long> tableIds = order.getTableIdList();
            for (Long tableId : tableIds) {
                restaurantTableRepository.findById(tableId).ifPresent(table -> {
                    table.setStatus(RestaurantTable.TableStatus.OCCUPIED);
                    restaurantTableRepository.save(table);
                    log.info("Table {} re-occupied for reverted order {}", table.getTableNumber(), order.getOrderNumber());
                });
            }
        } catch (Exception e) {
            log.error("Failed to re-occupy tables for reverted order {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }
}
