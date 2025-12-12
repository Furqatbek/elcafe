package com.elcafe.modules.order.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderStatusHistory;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            case NEW -> next == OrderStatus.ACCEPTED || next == OrderStatus.CANCELLED;
            case ACCEPTED -> next == OrderStatus.PREPARING || next == OrderStatus.CANCELLED;
            case PREPARING -> next == OrderStatus.READY || next == OrderStatus.CANCELLED;
            case READY -> next == OrderStatus.COURIER_ASSIGNED || next == OrderStatus.CANCELLED;
            case COURIER_ASSIGNED -> next == OrderStatus.ON_DELIVERY || next == OrderStatus.CANCELLED;
            case ON_DELIVERY -> next == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
