package com.elcafe.modules.waiter.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.promotion.dto.ApplyDiscountRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponResponse;
import com.elcafe.modules.promotion.service.CouponValidationService;
import com.elcafe.modules.promotion.service.DiscountCalculationService;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.DailyOrderSequenceService;
import com.elcafe.modules.order.service.OrderJsonHydration;
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
import com.elcafe.modules.waiter.event.OrderEventPublisher;
import com.elcafe.modules.waiter.enums.OrderEventType;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import com.elcafe.modules.marketing.event.OrderCompletionEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private final OrderCompletionEvents orderCompletionEvents;
    private final RestaurantTableRepository tableRepository;
    private final WaiterRepository waiterRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderEventService orderEventService;
    private final OrderEventPublisher orderEventPublisher;
    private final InventoryService inventoryService;
    private final DiscountCalculationService discountCalculationService;
    private final CouponValidationService couponValidationService;
    private final WaiterCommissionService waiterCommissionService;
    /**
     * The one order-number source, shared with OrderService and POSOrderService. See
     * {@link #createOrder} — this service used to mint its own, in a different format and without
     * uniqueness.
     */
    private final DailyOrderSequenceService dailyOrderSequenceService;

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
                .orderNumber(dailyOrderSequenceService.generateNextOrderNumber())
                .restaurant(table.getRestaurant())
                .customer(customer)
                .diningTable(table)
                .waiter(waiter)
                .status(OrderStatus.NEW)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .guestCount(request.getGuestCount())
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
                        .addOns(itemRequest.getAddOns()) // Keep for backward compatibility
                        .specialInstructions(itemRequest.getSpecialInstructions())
                        .build();

                // Add structured modifiers if provided
                if (itemRequest.getModifiers() != null && !itemRequest.getModifiers().isEmpty()) {
                    for (AddOrderItemRequest.AddOnInfo modifier : itemRequest.getModifiers()) {
                        orderItem.addAddOn(
                                modifier.getAddOnId(),
                                modifier.getName(),
                                modifier.getPrice() != null ? modifier.getPrice() : java.math.BigDecimal.ZERO,
                                modifier.getQuantity() != null ? modifier.getQuantity() : 1
                        );
                    }
                }

                savedOrder.addItem(orderItem);
            }

            // Recalculate totals if items were added
            recalculateOrderTotals(savedOrder);
            savedOrder = orderRepository.save(savedOrder);

            // Check ingredient availability and deduct inventory for items
            log.info("Checking ingredient availability for new order {}", savedOrder.getOrderNumber());
            if (!inventoryService.checkIngredientAvailability(savedOrder)) {
                List<String> missingIngredients = new ArrayList<>();
                for (var item : savedOrder.getItems()) {
                    missingIngredients.addAll(
                        inventoryService.getMissingIngredients(item.getProductId(), item.getQuantity())
                    );
                }
                String errorMsg = "Insufficient ingredients: " + String.join(", ", missingIngredients);
                log.error("Cannot create order with items: {}", errorMsg);
                throw new BadRequestException(errorMsg);
            }

            // Deduct ingredients from inventory
            try {
                inventoryService.deductIngredientsForOrder(savedOrder);
                log.info("Deducted ingredients for new order {} with {} items",
                    savedOrder.getOrderNumber(), savedOrder.getItems().size());

                // Auto-submit to kitchen since inventory is now deducted
                savedOrder.setStatus(OrderStatus.PREPARING);
                savedOrder = orderRepository.save(savedOrder);
                log.info("Order {} auto-submitted to kitchen with items", savedOrder.getOrderNumber());
            } catch (Exception e) {
                log.error("Failed to deduct ingredients for order {}: {}",
                    savedOrder.getOrderNumber(), e.getMessage());
                throw new BadRequestException("Failed to process inventory: " + e.getMessage());
            }
        }

        // Record event and broadcast via WebSocket
        orderEventService.recordEvent(savedOrder, OrderEventType.ORDER_CREATED, waiter.getName());
        orderEventPublisher.publishOrderCreated(savedOrder, waiter.getName());

        log.info("Created order {} for table {} by waiter {} with {} items",
                savedOrder.getOrderNumber(), table.getTableNumber(), waiter.getName(),
                savedOrder.getItems().size());

        return OrderJsonHydration.forJson(savedOrder);
    }

    /**
     * Add items to an order
     * If order is already submitted to kitchen (PREPARING+), deducts ingredients for new items
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

        // Track if order is currently a draft (NEW status) - will auto-submit after adding items
        boolean isNewOrder = order.getStatus() == OrderStatus.NEW;

        // Build list of new order items first (for inventory check)
        List<OrderItem> newItems = new ArrayList<>();

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
                    .addOns(itemRequest.getAddOns()) // Keep for backward compatibility
                    .specialInstructions(itemRequest.getSpecialInstructions())
                    .build();

            // Add structured modifiers if provided
            if (itemRequest.getModifiers() != null && !itemRequest.getModifiers().isEmpty()) {
                for (AddOrderItemRequest.AddOnInfo modifier : itemRequest.getModifiers()) {
                    orderItem.addAddOn(
                            modifier.getAddOnId(),
                            modifier.getName(),
                            modifier.getPrice() != null ? modifier.getPrice() : java.math.BigDecimal.ZERO,
                            modifier.getQuantity() != null ? modifier.getQuantity() : 1
                    );
                }
            }

            newItems.add(orderItem);
        }

        // Add items to order first
        for (OrderItem item : newItems) {
            order.addItem(item);
        }

        // Recalculate totals
        recalculateOrderTotals(order);

        // Check availability for new items
        for (OrderItem item : newItems) {
            if (!inventoryService.canMakeProduct(item.getProductId(), item.getQuantity())) {
                List<String> missing = inventoryService.getMissingIngredients(item.getProductId(), item.getQuantity());
                throw new BadRequestException("Insufficient ingredients for " + item.getProductName() + ": " + String.join(", ", missing));
            }
        }

        // Create a temporary order with just new items to deduct ingredients
        Order tempOrder = Order.builder()
                .orderNumber(order.getOrderNumber() + "-ADD")
                .items(new ArrayList<>(newItems))
                .build();

        try {
            inventoryService.deductIngredientsForOrder(tempOrder);
            log.info("Deducted ingredients for {} new items added to order {}", newItems.size(), order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to deduct ingredients for new items in order {}: {}", order.getOrderNumber(), e.getMessage());
            throw new BadRequestException("Failed to process inventory: " + e.getMessage());
        }

        // Auto-submit to kitchen if order was NEW
        if (isNewOrder) {
            order.setStatus(OrderStatus.PREPARING);
            log.info("Order {} auto-submitted to kitchen after adding items", order.getOrderNumber());
        }

        Order updatedOrder = orderRepository.save(order);

        // Record event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("itemsAdded", items.size());
        orderEventService.publishEvent(updatedOrder, OrderEventType.ORDER_UPDATED, waiter.getName(), metadata);

        log.info("Added {} items to order {}", items.size(), order.getOrderNumber());

        return OrderJsonHydration.forJson(updatedOrder);
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
            if (request.getQuantity() < 1) {
                // Quantity 0 or negative means remove the item
                order.getItems().remove(item);
            } else {
                item.setQuantity(request.getQuantity());
                item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
            }
        }

        if (request.getAddOns() != null) {
            item.setAddOns(request.getAddOns());
        }

        // Update structured modifiers if provided
        if (request.getModifiers() != null) {
            item.clearAddOns();
            for (UpdateOrderItemRequest.AddOnInfo modifier : request.getModifiers()) {
                item.addAddOn(
                        modifier.getAddOnId(),
                        modifier.getName(),
                        modifier.getPrice() != null ? modifier.getPrice() : java.math.BigDecimal.ZERO,
                        modifier.getQuantity() != null ? modifier.getQuantity() : 1
                );
            }
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

        return OrderJsonHydration.forJson(updatedOrder);
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

        return OrderJsonHydration.forJson(updatedOrder);
    }

    /**
     * Submit order to kitchen
     * Checks ingredient availability and deducts ingredients from inventory
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

        // Check ingredient availability before submitting to kitchen
        log.info("Checking ingredient availability for order {}", order.getOrderNumber());
        if (!inventoryService.checkIngredientAvailability(order)) {
            // Get detailed missing ingredients info
            List<String> missingIngredients = new ArrayList<>();
            for (var item : order.getItems()) {
                missingIngredients.addAll(
                    inventoryService.getMissingIngredients(item.getProductId(), item.getQuantity())
                );
            }
            String errorMsg = "Insufficient ingredients: " + String.join(", ", missingIngredients);
            log.error("Cannot submit order {} to kitchen: {}", order.getOrderNumber(), errorMsg);
            throw new BadRequestException(errorMsg);
        }

        // Deduct ingredients from inventory
        try {
            inventoryService.deductIngredientsForOrder(order);
            log.info("Successfully deducted ingredients for order {}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to deduct ingredients for order {}: {}", order.getOrderNumber(), e.getMessage());
            throw new BadRequestException("Failed to process inventory: " + e.getMessage());
        }

        order.setStatus(OrderStatus.PREPARING);

        // Update table status
        if (order.getDiningTable() != null) {
            order.getDiningTable().setStatus(TableStatus.OCCUPIED);
            tableRepository.save(order.getDiningTable());
        }

        Order updatedOrder = orderRepository.save(order);

        // Record event and broadcast via WebSocket
        orderEventService.recordEvent(updatedOrder, OrderEventType.ORDER_SUBMITTED_TO_KITCHEN, waiter.getName());
        orderEventPublisher.publishOrderSubmitted(updatedOrder, waiter.getName());

        log.info("Submitted order {} to kitchen by waiter {}", order.getOrderNumber(), waiter.getName());

        return OrderJsonHydration.forJson(updatedOrder);
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

        return OrderJsonHydration.forJson(order);
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

        // Record event and broadcast via WebSocket
        orderEventService.recordEvent(order, OrderEventType.BILL_REQUESTED, waiter.getName());
        orderEventPublisher.publishBillRequested(order, null, waiter.getName());

        log.info("Bill requested for order {} by waiter {}", order.getOrderNumber(), waiter.getName());

        return OrderJsonHydration.forJson(order);
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

        // Idempotent re-close: a double-tap or client retry on an already-closed order must not
        // replay the close side effects (duplicate ORDER_CLOSED event row + OrderPaid broadcast;
        // commission and the completion event carry their own per-order guards, this makes the
        // endpoint itself safe).
        if (order.getStatus() == OrderStatus.COMPLETED) {
            log.info("Order {} is already closed; close is a no-op", order.getOrderNumber());
            return order;
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot close a cancelled order");
        }

        // Verify order has been paid before closing
        if (!order.isFullyPaid()) {
            throw new BadRequestException("Cannot close order — payment has not been recorded. " +
                    "Process payment before closing the order.");
        }

        order.setStatus(OrderStatus.COMPLETED);

        // Update table status to cleaning
        if (order.getDiningTable() != null) {
            order.getDiningTable().setStatus(TableStatus.CLEANING);
            tableRepository.save(order.getDiningTable());
        }

        Order updatedOrder = orderRepository.save(order);

        // Record event and broadcast via WebSocket
        orderEventService.recordEvent(updatedOrder, OrderEventType.ORDER_CLOSED, waiter.getName());
        orderEventPublisher.publishOrderPaid(updatedOrder, updatedOrder.getTotal(),
                "COMPLETED", null, waiter.getName());

        // Calculate commission for the waiter
        waiterCommissionService.calculateCommissionForOrder(updatedOrder)
                .ifPresent(commission -> log.info("Commission calculated for order {}: {} ({}%)",
                        order.getOrderNumber(), commission.getCommissionAmount(), commission.getCommissionPercent()));

        // Loyalty/marketing completion chain (audit FUNC-15): durable fire-once marker — a re-close
        // of an already-COMPLETED order is a no-op here.
        if (orderCompletionEvents != null) {
            orderCompletionEvents.publishIfQualified(updatedOrder);
        }

        log.info("Closed order {} by waiter {}", order.getOrderNumber(), waiter.getName());

        return OrderJsonHydration.forJson(updatedOrder);
    }

    /**
     * Get order by ID
     */
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return OrderJsonHydration.forJson(orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId)));
    }

    /**
     * Get orders for a table
     */
    @Transactional(readOnly = true)
    public List<Order> getTableOrders(Long tableId) {
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + tableId));

        return OrderJsonHydration.forJson(table.getOrders().stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                .toList());
    }

    /**
     * Get order history for a waiter (all orders)
     */
    @Transactional(readOnly = true)
    public List<Order> getWaiterOrderHistory(Long waiterId) {
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        return OrderJsonHydration.forJson(orderRepository.findByWaiterWithItemsOrderByCreatedAtDesc(waiter));
    }

    /**
     * Get ongoing orders for a waiter (active orders)
     */
    @Transactional(readOnly = true)
    public List<Order> getWaiterOngoingOrders(Long waiterId) {
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        return OrderJsonHydration.forJson(orderRepository.findByWaiterAndStatusInWithItemsOrderByCreatedAtDesc(
                waiter,
                List.of(OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.ON_DELIVERY)
        ));
    }

    /**
     * Get waiter performance metrics with period filter
     * @param waiterId the waiter ID
     * @param period the time period: "daily", "weekly", or "monthly"
     */
    @Transactional(readOnly = true)
    public WaiterMetricsResponse getWaiterMetrics(Long waiterId, String period) {
        // Verify waiter exists
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        // Calculate start date based on period
        OffsetDateTime startDate = calculateStartDate(period);
        int activityDays = getActivityDays(period);

        // Calculate total revenue for the period
        BigDecimal totalRevenue = orderRepository.calculateTotalRevenueByWaiterSince(waiterId, startDate);

        // Count valid orders for the period
        Long totalOrders = orderRepository.countValidOrdersByWaiterSince(waiterId, startDate);

        // Calculate average ticket
        BigDecimal averageTicket = BigDecimal.ZERO;
        if (totalOrders > 0) {
            averageTicket = totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
        }

        // Get activity data for the period
        OffsetDateTime activityStartDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(activityDays);
        List<Object[]> dailyData = orderRepository.findDailyRevenueByWaiter(waiterId, activityStartDate);

        List<DailyRevenueData> activity = dailyData.stream()
                .map(row -> DailyRevenueData.builder()
                        .date((LocalDate) row[0])
                        .revenue((BigDecimal) row[1])
                        .orderCount((Long) row[2])
                        .build())
                .collect(Collectors.toList());

        // Get recent transactions within the period (last 5 orders)
        List<Order> recentOrders = orderRepository.findRecentOrdersByWaiterSince(waiterId, startDate, PageRequest.of(0, 5));

        List<RecentTransactionData> recentTransactions = recentOrders.stream()
                .map(order -> RecentTransactionData.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .tableId(order.getDiningTable() != null ? order.getDiningTable().getId() : null)
                        .tableNumber(order.getDiningTable() != null ? order.getDiningTable().getTableNumber() : null)
                        .createdAt(order.getCreatedAt().toLocalDateTime())
                        .status(order.getStatus())
                        .total(order.getTotal())
                        .build())
                .collect(Collectors.toList());

        return WaiterMetricsResponse.builder()
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .averageTicket(averageTicket)
                .weeklyActivity(activity)
                .recentTransactions(recentTransactions)
                .build();
    }

    /**
     * Calculate the start date based on the period parameter
     */
    private OffsetDateTime calculateStartDate(String period) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return switch (period.toLowerCase()) {
            case "daily" -> now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
            case "monthly" -> now.minusDays(30).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
            default -> now.minusDays(7).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC); // weekly (default)
        };
    }

    /**
     * Get the number of days for activity chart based on period
     */
    private int getActivityDays(String period) {
        return switch (period.toLowerCase()) {
            case "daily" -> 1;
            case "monthly" -> 30;
            default -> 7; // weekly (default)
        };
    }

    // ==================== DISCOUNT METHODS ====================

    /**
     * Apply a discount to an order (coupon, promotion, manual, or happy hour)
     */
    @Transactional
    public Order applyDiscount(Long orderId, ApplyDiscountRequest request, Long waiterId) {
        log.info("Waiter {} applying discount to order {}: type={}", waiterId, orderId, request.getDiscountType());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        // Check order status allows modification
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot modify completed or cancelled order");
        }

        // Apply discount using the discount calculation service
        discountCalculationService.applyDiscount(order, request);

        // Recalculate totals
        recalculateOrderTotals(order);

        // Save the order
        Order savedOrder = orderRepository.save(order);

        // Record event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("discountType", request.getDiscountType().name());
        metadata.put("discountAmount", savedOrder.getDiscount());
        orderEventService.publishEvent(savedOrder, OrderEventType.ORDER_UPDATED, waiter.getName(), metadata);

        log.info("Discount applied to order {} by waiter {}: discount={}, newTotal={}",
                orderId, waiter.getName(), savedOrder.getDiscount(), savedOrder.getTotal());

        return OrderJsonHydration.forJson(savedOrder);
    }

    /**
     * Remove discount from an order
     */
    @Transactional
    public Order removeDiscount(Long orderId, Long waiterId) {
        log.info("Waiter {} removing discount from order {}", waiterId, orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        // Check order status allows modification
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot modify completed or cancelled order");
        }

        // Clear discount fields
        order.setDiscount(BigDecimal.ZERO);
        order.setDiscountType(null);
        order.setCouponCode(null);
        order.setPromotionId(null);

        // Recalculate totals
        recalculateOrderTotals(order);

        // Save the order
        Order savedOrder = orderRepository.save(order);

        // Record event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", "discount_removed");
        orderEventService.publishEvent(savedOrder, OrderEventType.ORDER_UPDATED, waiter.getName(), metadata);

        log.info("Discount removed from order {} by waiter {}", orderId, waiter.getName());

        return OrderJsonHydration.forJson(savedOrder);
    }

    /**
     * Validate a coupon code for an order without applying it
     */
    @Transactional(readOnly = true)
    public ValidateCouponResponse validateCoupon(Long orderId, String couponCode) {
        log.info("Validating coupon {} for waiter order {}", couponCode, orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        // Build validation request from order
        ValidateCouponRequest validateRequest = ValidateCouponRequest.builder()
                .code(couponCode)
                .restaurantId(order.getRestaurant().getId())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .orderSubtotal(order.getSubtotal())
                .orderType(order.getOrderType() != null ? order.getOrderType().name() : null)
                .items(order.getItems().stream()
                        .map(item -> ValidateCouponRequest.OrderItemInfo.builder()
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .price(item.getUnitPrice())
                                .build())
                        .collect(Collectors.toList()))
                .build();

        return couponValidationService.validateCoupon(validateRequest);
    }

    // The private generator that used to live here is gone. It produced
    //     String.format("W%d%03d", System.currentTimeMillis() % 1000000, random(0..999))
    // which was neither unique nor consistent:
    //
    //   * orders.order_number carries a UNIQUE index and this insert has no retry, so a collision was
    //     a 500 for the waiter mid-service. The timestamp component wraps every ~16.7 minutes
    //     (millis % 1_000_000), leaving three random digits to separate two orders placed in the same
    //     millisecond — roughly a 1-in-1000 shot per such pair, on the busiest path in the product.
    //   * It also read "W123456789" while every other path produced "ORD-20251220-0001", so receipts
    //     and support lookups disagreed depending on who took the order.
    //
    // DailyOrderSequenceService is the shared source (a per-date row taken with a pessimistic lock),
    // already used by OrderService and POSOrderService.

    /**
     * Recalculate order totals
     */
    private void recalculateOrderTotals(Order order) {
        BigDecimal subtotal = order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setSubtotal(subtotal);

        // No tax
        BigDecimal tax = BigDecimal.ZERO;
        order.setTax(tax);

        // Calculate total
        BigDecimal discount = order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO;
        BigDecimal total = subtotal.add(tax).subtract(discount);
        order.setTotal(total);

        // Keep grandTotal in sync so payment validation uses the correct amount
        BigDecimal tip = order.getTipAmount() != null ? order.getTipAmount() : BigDecimal.ZERO;
        order.setGrandTotal(total.add(tip));
    }
}
