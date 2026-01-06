package com.elcafe.modules.order.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.financial.service.RevenueService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elcafe.modules.order.specification.OrderSpecification;
import org.springframework.data.jpa.domain.Specification;

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
    private final BusinessDayService businessDayService;

    @Autowired
    @Lazy
    private RevenueService revenueService;

    @Autowired
    @Lazy
    private PrintService printService;

    @Autowired
    @Lazy
    private InventoryValuationService inventoryValuationService;

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
        // Also check for tableIds/diningTable as fallback for orders without orderType set
        boolean isDineInOrder = order.getOrderType() == OrderType.DINE_IN ||
                (order.getTableIds() != null && !order.getTableIds().isBlank()) ||
                order.getDiningTable() != null;
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

    /**
     * Get orders with filters for order history page.
     * Supports filtering by restaurant, status, date range, and search term.
     * Date ranges are adjusted to business day boundaries based on restaurant working hours.
     */
    @Transactional(readOnly = true)
    public Page<Order> getOrdersWithFilters(
            Long restaurantId,
            OrderStatus status,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String search,
            Pageable pageable
    ) {
        log.info("Fetching orders with filters: restaurantId={}, status={}, fromDate={}, toDate={}, search={}",
                restaurantId, status, fromDate, toDate, search);

        // Adjust date range to business day boundaries
        LocalDateTime adjustedFromDate = fromDate;
        LocalDateTime adjustedToDate = toDate;

        if (fromDate != null || toDate != null) {
            BusinessDayService.DateRange adjustedRange = businessDayService.adjustToBusinessDayBoundaries(
                    restaurantId, fromDate, toDate
            );
            adjustedFromDate = adjustedRange.from();
            adjustedToDate = adjustedRange.to();

            log.info("Adjusted to business day boundaries: {} to {} -> {} to {}",
                    fromDate, toDate, adjustedFromDate, adjustedToDate);
        }

        Specification<Order> spec = OrderSpecification.withFilters(
                restaurantId,
                status,
                adjustedFromDate,
                adjustedToDate,
                search
        );

        return orderRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByRestaurant(Long restaurantId) {
        // Get business day boundaries for 7 days ago to now
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        BusinessDayService.DateRange adjustedRange = businessDayService.adjustToBusinessDayBoundaries(
                restaurantId, sevenDaysAgo, LocalDateTime.now()
        );

        return orderRepository.findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
                restaurantId,
                adjustedRange.from(),
                adjustedRange.to()
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
            // Release tables from tableIds field (for multi-table orders)
            if (order.getTableIds() != null && !order.getTableIds().isBlank()) {
                String[] tableIdStrings = order.getTableIds().split(",");
                for (String tableIdStr : tableIdStrings) {
                    Long tableId = Long.parseLong(tableIdStr.trim());
                    restaurantTableRepository.findById(tableId).ifPresent(table -> {
                        table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
                        restaurantTableRepository.save(table);
                        log.info("Table {} released (set to AVAILABLE) for order {}", table.getTableNumber(), order.getOrderNumber());
                    });
                }
            }
            // Also check the diningTable field for backwards compatibility
            else if (order.getDiningTable() != null) {
                RestaurantTable table = order.getDiningTable();
                table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
                restaurantTableRepository.save(table);
                log.info("Table {} released (set to AVAILABLE) for order {}", table.getTableNumber(), order.getOrderNumber());
            }
        } catch (Exception e) {
            log.error("Failed to release tables for order {}: {}", order.getOrderNumber(), e.getMessage());
            // Don't fail the order status update if table release fails
        }
    }
}
