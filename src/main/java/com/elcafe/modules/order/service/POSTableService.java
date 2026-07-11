package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import com.elcafe.modules.marketing.event.OrderCompletionEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service responsible for table management operations.
 * Handles table assignment, changes, and release for orders.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class POSTableService {

    private final OrderRepository orderRepository;
    private final OrderCompletionEvents orderCompletionEvents;
    private final RestaurantTableRepository restaurantTableRepository;

    /**
     * Close an order and release the associated table(s)
     */
    @Transactional
    public Order closeOrderAndReleaseTable(Long orderId) {
        log.info("Closing order and releasing table for order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Verify order has been paid before closing
        if (!order.isFullyPaid() && order.getStatus() != OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot close order — payment has not been recorded. " +
                    "Process payment before closing the order.");
        }

        // Update order status to DELIVERED/COMPLETED if not already
        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.DELIVERED);
            order.setCompletedAt(OffsetDateTime.now());
        }

        // Release all tables associated with this order
        releaseTablesForOrder(order);

        Order saved = orderRepository.save(order);

        // Loyalty/marketing completion chain (audit FUNC-15): normally the qualifying moment fired
        // when PaymentService recorded full payment; the gate's marker makes this call a no-op then.
        if (orderCompletionEvents != null) {
            orderCompletionEvents.publishIfQualified(saved);
        }

        return saved;
    }

    /**
     * Change the table for an order
     */
    @Transactional
    public Order changeTable(Long orderId, Long newTableId) {
        log.info("Changing table for order {}: newTableId={}", orderId, newTableId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Validate order is not closed
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot change table for a closed or cancelled order");
        }

        // Find the new table
        RestaurantTable newTable = restaurantTableRepository.findById(newTableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with ID: " + newTableId));

        // Check if new table is available or the same as current
        Long currentTableId = order.getDiningTable() != null ? order.getDiningTable().getId() : null;
        if (newTableId.equals(currentTableId)) {
            // Same table, no change needed
            return order;
        }

        if (newTable.getStatus() == RestaurantTable.TableStatus.OCCUPIED) {
            throw new IllegalArgumentException("Table " + newTable.getTableNumber() + " is already occupied");
        }

        // Release all current tables except the new one
        List<Long> currentTableIds = order.getTableIdList();
        for (Long tableId : currentTableIds) {
            if (!tableId.equals(newTableId)) {
                restaurantTableRepository.findById(tableId).ifPresent(table -> {
                    table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
                    restaurantTableRepository.save(table);
                    log.info("Released table {}", table.getTableNumber());
                });
            }
        }

        // Clear existing tables and assign new table
        order.clearTables();
        newTable.setStatus(RestaurantTable.TableStatus.OCCUPIED);
        restaurantTableRepository.save(newTable);
        order.addTable(newTable, true);
        log.info("Assigned new table {} to order {}", newTable.getTableNumber(), orderId);

        return orderRepository.save(order);
    }

    /**
     * Assign tables to an order
     */
    @Transactional
    public void assignTablesToOrder(Order order, List<Long> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) {
            return;
        }

        boolean isFirst = true;
        for (Long tableId : tableIds) {
            RestaurantTable table = restaurantTableRepository.findById(tableId).orElse(null);
            if (table != null) {
                order.addTable(table, isFirst);
                isFirst = false;

                table.setStatus(RestaurantTable.TableStatus.OCCUPIED);
                restaurantTableRepository.save(table);
                log.info("Table {} marked as OCCUPIED for order", table.getTableNumber());
            }
        }
    }

    /**
     * Release all tables for an order
     */
    @Transactional
    public void releaseTablesForOrder(Order order) {
        List<Long> tableIdList = order.getTableIdList();
        for (Long tableId : tableIdList) {
            restaurantTableRepository.findById(tableId).ifPresent(table -> {
                table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
                restaurantTableRepository.save(table);
                log.info("Table {} marked as AVAILABLE", table.getTableNumber());
            });
        }
    }
}
