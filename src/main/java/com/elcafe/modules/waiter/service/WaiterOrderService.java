package com.elcafe.modules.waiter.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.waiter.dto.AddOrderItemRequest;
import com.elcafe.modules.waiter.dto.CreateOrderRequest;
import com.elcafe.modules.waiter.dto.DailyRevenueData;
import com.elcafe.modules.waiter.dto.RecentTransactionData;
import com.elcafe.modules.waiter.dto.UpdateOrderItemRequest;
import com.elcafe.modules.waiter.dto.WaiterMetricsResponse;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.entity.RestaurantTable.TableStatus;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.enums.OrderEventType;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing orders from waiter's perspective
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaiterOrderService {

    private final OrderRepository orderRepository;
    private final RestaurantTableRepository tableRepository;
    private final WaiterRepository waiterRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderEventService orderEventService;

    /**
     * Create a new order for a table (with optional items)
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request, Long waiterId) {
        RestaurantTable table = tableRepository.findById(request.getTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + request.getTableId()));

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        // Customer is optional - can be added later
        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + request.getCustomerId()));
        }

        // Update table status to OCCUPIED when order is created
        if (table.getStatus() == TableStatus.AVAILABLE) {
            table.setStatus(TableStatus.OCCUPIED);
        }
        tableRepository.save(table);

        // Create order
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .restaurant(table.getRestaurant())
                .customer(customer)
                .diningTable(table)
                .waiter(waiter)
                .status(OrderStatus.NEW)
                .orderSource(OrderSource.WAITER)
                .subtotal(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .customerNotes(request.getCustomerNotes())
                .items(new ArrayList<>())
                .build();

        Order savedOrder = orderRepository.save(order);

        // Add items if provided in the request
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (AddOrderItemRequest itemRequest : request.getItems()) {
                Product product = productRepository.findById(itemRequest.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemRequest.getProductId()));

                BigDecimal unitPrice = product.getPrice();
                String variantName = null;

                // Handle variant if specified
                if (itemRequest.getVariantId() != null) {
                    ProductVariant variant = productVariantRepository.findById(itemRequest.getVariantId())
                            .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + itemRequest.getVariantId()));
                    unitPrice = variant.getPrice();
                    variantName = variant.getName();
                }

                BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

                OrderItem orderItem = OrderItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .variantId(itemRequest.getVariantId())
                        .variantName(variantName)
                        .quantity(itemRequest.getQuantity())
                        .unitPrice(unitPrice)
                        .totalPrice(totalPrice)
                        .addOns(itemRequest.getAddOns())
                        .specialInstructions(itemRequest.getSpecialInstructions())
                        .build();

                savedOrder.addItem(orderItem);
            }

            // Recalculate totals if items were added
            recalculateOrderTotals(savedOrder);
            savedOrder = orderRepository.save(savedOrder);
        }

        // Record event
        orderEventService.recordEvent(savedOrder, OrderEventType.ORDER_CREATED, waiter.getName());

        log.info("Created order {} for table {} by waiter {} with {} items",
                savedOrder.getOrderNumber(), table.getTableNumber(), waiter.getName(),
                savedOrder.getItems().size());

        return savedOrder;
    }

    /**
     * Add items to an order
     */
    @Transactional
    public Order addItems(Long orderId, List<AddOrderItemRequest> items, Long waiterId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        // Check if order can be modified
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot modify completed or cancelled order");
        }

        for (AddOrderItemRequest itemRequest : items) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemRequest.getProductId()));

            BigDecimal unitPrice = product.getPrice();
            String variantName = null;

            // Handle variant if specified
            if (itemRequest.getVariantId() != null) {
                ProductVariant variant = productVariantRepository.findById(itemRequest.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + itemRequest.getVariantId()));
                unitPrice = variant.getPrice();
                variantName = variant.getName();
            }

            BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .productName(product.getName())
                    .variantId(itemRequest.getVariantId())
                    .variantName(variantName)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .totalPrice(totalPrice)
                    .addOns(itemRequest.getAddOns())
                    .specialInstructions(itemRequest.getSpecialInstructions())
                    .build();

            order.addItem(orderItem);
        }

        // Recalculate totals
        recalculateOrderTotals(order);

        Order updatedOrder = orderRepository.save(order);

        // Record event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("itemsAdded", items.size());
        orderEventService.publishEvent(updatedOrder, OrderEventType.ORDER_UPDATED, waiter.getName(), metadata);

        log.info("Added {} items to order {}", items.size(), order.getOrderNumber());

        return updatedOrder;
    }

    /**
     * Update an order item
     */
    @Transactional
    public Order updateItem(Long orderId, Long itemId, UpdateOrderItemRequest request, Long waiterId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + itemId));

        // Check if order can be modified
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot modify completed or cancelled order");
        }

        if (request.getQuantity() != null) {
            item.setQuantity(request.getQuantity());
            item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        }

        if (request.getAddOns() != null) {
            item.setAddOns(request.getAddOns());
        }

        if (request.getSpecialInstructions() != null) {
            item.setSpecialInstructions(request.getSpecialInstructions());
        }

        // Recalculate totals
        recalculateOrderTotals(order);

        Order updatedOrder = orderRepository.save(order);

        // Record event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("itemId", itemId);
        metadata.put("productName", item.getProductName());
        orderEventService.publishEvent(updatedOrder, OrderEventType.ORDER_UPDATED, waiter.getName(), metadata);

        log.info("Updated item {} in order {}", itemId, order.getOrderNumber());

        return updatedOrder;
    }

    /**
     * Remove an item from order
     */
    @Transactional
    public Order removeItem(Long orderId, Long itemId, Long waiterId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        // Check if order can be modified
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot modify completed or cancelled order");
        }

        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + itemId));

        String productName = item.getProductName();
        order.getItems().remove(item);

        // Recalculate totals
        recalculateOrderTotals(order);

        Order updatedOrder = orderRepository.save(order);

        // Record event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("itemId", itemId);
        metadata.put("productName", productName);
        orderEventService.publishEvent(updatedOrder, OrderEventType.ORDER_UPDATED, waiter.getName(), metadata);

        log.info("Removed item {} from order {}", itemId, order.getOrderNumber());

        return updatedOrder;
    }

    /**
     * Submit order to kitchen
     */
    @Transactional
    public Order submitToKitchen(Long orderId, Long waiterId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        if (order.getItems().isEmpty()) {
            throw new BadRequestException("Cannot submit order without items");
        }

        if (order.getStatus() != OrderStatus.NEW) {
            throw new BadRequestException("Order has already been submitted");
        }

        order.setStatus(OrderStatus.PREPARING);

        // Update table status
        if (order.getDiningTable() != null) {
            order.getDiningTable().setStatus(TableStatus.OCCUPIED);
            tableRepository.save(order.getDiningTable());
        }

        Order updatedOrder = orderRepository.save(order);

        // Record event
        orderEventService.recordEvent(updatedOrder, OrderEventType.ORDER_SUBMITTED_TO_KITCHEN, waiter.getName());

        log.info("Submitted order {} to kitchen by waiter {}", order.getOrderNumber(), waiter.getName());

        return updatedOrder;
    }

    /**
     * Mark item as delivered
     */
    @Transactional
    public Order markItemDelivered(Long orderId, Long itemId, Long waiterId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + itemId));

        // Update table status to served
        if (order.getDiningTable() != null && order.getDiningTable().getStatus() == TableStatus.OCCUPIED) {
            order.getDiningTable().setStatus(TableStatus.OCCUPIED);
            tableRepository.save(order.getDiningTable());
        }

        // Record event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("itemId", itemId);
        metadata.put("productName", item.getProductName());
        orderEventService.publishEvent(order, OrderEventType.ITEM_DELIVERED, waiter.getName(), metadata);

        log.info("Marked item {} as delivered in order {}", itemId, order.getOrderNumber());

        return order;
    }

    /**
     * Request bill for table
     */
    @Transactional
    public Order requestBill(Long orderId, Long waiterId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        // Update table status
        if (order.getDiningTable() != null) {
            order.getDiningTable().setStatus(TableStatus.RESERVED);
            tableRepository.save(order.getDiningTable());
        }

        // Record event
        orderEventService.recordEvent(order, OrderEventType.BILL_REQUESTED, waiter.getName());

        log.info("Bill requested for order {} by waiter {}", order.getOrderNumber(), waiter.getName());

        return order;
    }

    /**
     * Close order (after payment)
     */
    @Transactional
    public Order closeOrder(Long orderId, Long waiterId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        order.setStatus(OrderStatus.COMPLETED);

        // Update table status to cleaning
        if (order.getDiningTable() != null) {
            order.getDiningTable().setStatus(TableStatus.CLEANING);
            tableRepository.save(order.getDiningTable());
        }

        Order updatedOrder = orderRepository.save(order);

        // Record event
        orderEventService.recordEvent(updatedOrder, OrderEventType.ORDER_CLOSED, waiter.getName());

        log.info("Closed order {} by waiter {}", order.getOrderNumber(), waiter.getName());

        return updatedOrder;
    }

    /**
     * Get order by ID
     */
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }

    /**
     * Get orders for a table
     */
    @Transactional(readOnly = true)
    public List<Order> getTableOrders(Long tableId) {
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + tableId));

        return table.getOrders().stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                .toList();
    }

    /**
     * Get order history for a waiter (completed and cancelled orders)
     */
    @Transactional(readOnly = true)
    public List<Order> getWaiterOrderHistory(Long waiterId) {
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        return orderRepository.findByWaiterAndStatusInWithItemsOrderByCreatedAtDesc(
                waiter,
                List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED)
        );
    }

    /**
     * Get ongoing orders for a waiter (active orders)
     */
    @Transactional(readOnly = true)
    public List<Order> getWaiterOngoingOrders(Long waiterId) {
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        return orderRepository.findByWaiterAndStatusInWithItemsOrderByCreatedAtDesc(
                waiter,
                List.of(OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.ON_DELIVERY)
        );
    }

    /**
     * Get waiter performance metrics
     */
    @Transactional(readOnly = true)
    public WaiterMetricsResponse getWaiterMetrics(Long waiterId) {
        // Verify waiter exists
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        // Calculate total revenue (excluding PENDING and CANCELLED)
        BigDecimal totalRevenue = orderRepository.calculateTotalRevenueByWaiter(waiterId);

        // Count valid orders (excluding PENDING and CANCELLED)
        Long totalOrders = orderRepository.countValidOrdersByWaiter(waiterId);

        // Calculate average ticket
        BigDecimal averageTicket = BigDecimal.ZERO;
        if (totalOrders > 0) {
            averageTicket = totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
        }

        // Get weekly activity (last 7 days)
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Object[]> dailyData = orderRepository.findDailyRevenueByWaiter(waiterId, sevenDaysAgo);

        List<DailyRevenueData> weeklyActivity = dailyData.stream()
                .map(row -> DailyRevenueData.builder()
                        .date(((Date) row[0]).toLocalDate())
                        .revenue((BigDecimal) row[1])
                        .orderCount((Long) row[2])
                        .build())
                .collect(Collectors.toList());

        // Get recent transactions (last 5 orders)
        List<Order> recentOrders = orderRepository.findRecentOrdersByWaiter(waiterId, PageRequest.of(0, 5));

        List<RecentTransactionData> recentTransactions = recentOrders.stream()
                .map(order -> RecentTransactionData.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .tableId(order.getDiningTable() != null ? order.getDiningTable().getId() : null)
                        .tableNumber(order.getDiningTable() != null ? order.getDiningTable().getTableNumber() : null)
                        .createdAt(order.getCreatedAt())
                        .status(order.getStatus())
                        .total(order.getTotal())
                        .build())
                .collect(Collectors.toList());

        return WaiterMetricsResponse.builder()
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .averageTicket(averageTicket)
                .weeklyActivity(weeklyActivity)
                .recentTransactions(recentTransactions)
                .build();
    }

    /**
     * Generate unique order number (short format)
     */
    private String generateOrderNumber() {
        long timestamp = System.currentTimeMillis();
        int random = (int) (Math.random() * 1000);
        return String.format("W%d%03d", timestamp % 1000000, random);
    }

    /**
     * Recalculate order totals
     */
    private void recalculateOrderTotals(Order order) {
        BigDecimal subtotal = order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setSubtotal(subtotal);

        // Calculate tax (assuming 10% tax rate)
        BigDecimal tax = subtotal.multiply(BigDecimal.valueOf(0.10));
        order.setTax(tax);

        // Calculate total
        BigDecimal total = subtotal.add(tax).subtract(order.getDiscount());
        order.setTotal(total);
    }
}
