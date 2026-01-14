package com.elcafe.modules.order.service;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.repository.KitchenOrderRepository;
import com.elcafe.modules.kitchen.service.KitchenOrderService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.order.dto.pos.POSKitchenStatusDTO;
import com.elcafe.modules.order.dto.pos.POSProductAvailabilityDTO;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.dto.pos.CreatePOSOrderRequest;
import com.elcafe.modules.order.dto.pos.ModifyOrderItemRequest;
import com.elcafe.modules.order.dto.pos.POSOrderResponse;
import com.elcafe.modules.order.dto.pos.SplitBillDTO;
import com.elcafe.modules.order.entity.DeliveryInfo;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class POSOrderService {

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final InventoryService inventoryService;
    private final InventoryProductIngredientRepository productIngredientRepository;
    private final KitchenOrderService kitchenOrderService;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final DailyOrderSequenceService dailyOrderSequenceService;

    @Transactional
    public POSOrderResponse createOrder(CreatePOSOrderRequest request) {
        log.info("Creating POS order: type={}, restaurant={}", request.getOrderType(), request.getRestaurantId());

        // Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found with ID: " + request.getRestaurantId()));

        // Find or create customer (optional for dine-in)
        Customer customer = findOrCreateCustomer(request.getCustomerInfo(), request.getOrderType());

        // Create order
        Order order = new Order();
        order.setOrderNumber(dailyOrderSequenceService.generateNextOrderNumber());
        order.setRestaurant(restaurant);
        order.setCustomer(customer);
        order.setStatus(OrderStatus.NEW);
        order.setOrderType(OrderType.valueOf(request.getOrderType().name()));
        order.setCustomerNotes(request.getOrderNotes());

        // Set pricing - default all fees to 0
        order.setSubtotal(request.getSubtotal() != null ? request.getSubtotal() : BigDecimal.ZERO);
        order.setTax(request.getTax() != null ? request.getTax() : BigDecimal.ZERO);
        order.setDeliveryFee(request.getDeliveryFee() != null ? request.getDeliveryFee() : BigDecimal.ZERO);
        order.setServiceFeePercent(request.getServiceFeePercent() != null ? request.getServiceFeePercent() : BigDecimal.ZERO);
        order.setServiceFee(request.getServiceFee() != null ? request.getServiceFee() : BigDecimal.ZERO);
        order.setEntryFee(request.getEntryFee() != null ? request.getEntryFee() : BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO); // Discount applied separately if needed
        order.setTotal(request.getTotal() != null ? request.getTotal() : BigDecimal.ZERO);

        // Add order items
        List<OrderItem> orderItems = request.getItems().stream()
                .map(itemReq -> createOrderItem(itemReq, order))
                .collect(Collectors.toList());

        // Set items using the provided method
        for (OrderItem item : orderItems) {
            order.addItem(item);
        }

        // Handle delivery-specific info
        if (request.getOrderType() == CreatePOSOrderRequest.OrderType.DELIVERY) {
            if (request.getDeliveryInfo() == null) {
                throw new IllegalArgumentException("Delivery information is required for delivery orders");
            }
            DeliveryInfo deliveryInfo = createDeliveryInfo(request.getDeliveryInfo(), order);
            order.setDeliveryInfo(deliveryInfo);
        }

        // Handle dine-in specific info (table selection)
        if (request.getOrderType() == CreatePOSOrderRequest.OrderType.DINE_IN) {
            if (request.getDineInInfo() != null) {
                // Set guest count
                order.setGuestCount(request.getDineInInfo().getGuestCount());

                // Handle multi-table selection
                List<Long> tableIds = request.getDineInInfo().getTableIds();
                if (tableIds != null && !tableIds.isEmpty()) {
                    // Store comma-separated table IDs
                    order.setTableIds(tableIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")));

                    // Set first table as the primary dining table (for backwards compatibility)
                    RestaurantTable firstTable = restaurantTableRepository.findById(tableIds.get(0))
                            .orElse(null);
                    if (firstTable != null) {
                        order.setDiningTable(firstTable);
                    }

                    // Mark all selected tables as OCCUPIED
                    for (Long tableId : tableIds) {
                        restaurantTableRepository.findById(tableId).ifPresent(table -> {
                            table.setStatus(RestaurantTable.TableStatus.OCCUPIED);
                            restaurantTableRepository.save(table);
                            log.info("Table {} marked as OCCUPIED for order", table.getTableNumber());
                        });
                    }
                }
            }
        }

        // Check ingredient availability before saving
        // We need to save first to get the order with items, then check availability
        Order savedOrder = orderRepository.save(order);

        // Check inventory availability
        if (!inventoryService.checkIngredientAvailability(savedOrder)) {
            // Get detailed missing ingredients info
            List<String> allMissing = new java.util.ArrayList<>();
            for (OrderItem item : savedOrder.getItems()) {
                List<String> missing = inventoryService.getMissingIngredients(
                        item.getProductId(), item.getQuantity());
                allMissing.addAll(missing);
            }
            // Rollback the order by throwing exception (transaction will rollback)
            throw new IllegalStateException("Insufficient inventory: " + String.join("; ", allMissing));
        }

        // Deduct ingredients from inventory
        try {
            inventoryService.deductIngredientsForOrder(savedOrder);
            log.info("Inventory deducted successfully for order: {}", savedOrder.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to deduct inventory for order {}: {}", savedOrder.getOrderNumber(), e.getMessage());
            throw new IllegalStateException("Failed to deduct inventory: " + e.getMessage(), e);
        }

        // Kitchen order creation is skipped - orders go directly to payment flow
        // Kitchen can manually create orders if needed via the kitchen module
        log.info("Order created without kitchen order - direct payment flow enabled");

        // Force initialize lazy relationships
        savedOrder.getRestaurant().getName();
        if (savedOrder.getCustomer() != null) {
            savedOrder.getCustomer().getPhone();
        }
        savedOrder.getItems().size();
        savedOrder.getStatusHistory().size();

        log.info("POS order created successfully: {}", savedOrder.getOrderNumber());

        // Send notifications
        try {
            notificationService.notifyNewOrder(savedOrder);
        } catch (Exception e) {
            log.error("Failed to send notifications for order {}", savedOrder.getOrderNumber(), e);
        }

        return mapToResponse(savedOrder, request.getOrderType().name());
    }

    private Customer findOrCreateCustomer(CreatePOSOrderRequest.CustomerInfo customerInfo,
                                          CreatePOSOrderRequest.OrderType orderType) {
        // For dine-in orders, customer info is optional (walk-in guests)
        if (orderType == CreatePOSOrderRequest.OrderType.DINE_IN) {
            // If no phone provided, allow order without customer (walk-in guest)
            if (customerInfo == null || customerInfo.getPhone() == null || customerInfo.getPhone().isBlank()) {
                log.info("Dine-in order without customer info - walk-in guest");
                return null;
            }
        }

        // For other orders or if phone is provided, find or create by phone
        return customerRepository.findByPhone(customerInfo.getPhone())
                .orElseGet(() -> {
                    log.info("Creating new customer with phone: {}", customerInfo.getPhone());

                    // Split name into first and last name
                    String name = customerInfo.getName() != null ? customerInfo.getName().trim() : "Customer";
                    String[] nameParts = name.split("\\s+", 2);
                    String firstName = nameParts[0];
                    String lastName = nameParts.length > 1 ? nameParts[1] : "";

                    Customer newCustomer = new Customer();
                    newCustomer.setFirstName(firstName);
                    newCustomer.setLastName(lastName);
                    newCustomer.setPhone(customerInfo.getPhone());
                    newCustomer.setEmail(customerInfo.getEmail());
                    return customerRepository.save(newCustomer);
                });
    }

    private OrderItem createOrderItem(CreatePOSOrderRequest.OrderItemRequest itemRequest, Order order) {
        Product product = productRepository.findById(itemRequest.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + itemRequest.getProductId()));

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProductId(product.getId());
        orderItem.setProductName(product.getName());
        orderItem.setQuantity(itemRequest.getQuantity());
        orderItem.setUnitPrice(itemRequest.getPrice());
        orderItem.setTotalPrice(itemRequest.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
        orderItem.setSpecialInstructions(itemRequest.getNotes());

        // Add modifiers as add-ons JSON or text if present
        if (itemRequest.getModifiers() != null && !itemRequest.getModifiers().isEmpty()) {
            String modifiers = itemRequest.getModifiers().stream()
                    .map(m -> m.getName() + (m.getPrice().compareTo(BigDecimal.ZERO) > 0 ? " (+$" + m.getPrice() + ")" : ""))
                    .collect(Collectors.joining(", "));
            orderItem.setAddOns(modifiers);
        }

        return orderItem;
    }

    private DeliveryInfo createDeliveryInfo(CreatePOSOrderRequest.DeliveryInfo deliveryInfoReq, Order order) {
        DeliveryInfo deliveryInfo = new DeliveryInfo();
        deliveryInfo.setOrder(order);
        deliveryInfo.setAddress(deliveryInfoReq.getStreet());
        deliveryInfo.setCity(deliveryInfoReq.getCity());
        deliveryInfo.setState(deliveryInfoReq.getState());
        deliveryInfo.setZipCode(deliveryInfoReq.getZipCode());
        deliveryInfo.setDeliveryInstructions(deliveryInfoReq.getDeliveryInstructions());
        deliveryInfo.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(45));
        return deliveryInfo;
    }

    @Transactional(readOnly = true)
    public POSProductAvailabilityDTO getProductAvailability(Long productId, Long restaurantId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        // Get all product ingredients
        List<ProductIngredient> productIngredients =
                productIngredientRepository.findByProductIdWithIngredients(productId);

        // Calculate availability for each ingredient
        List<POSProductAvailabilityDTO.IngredientAvailability> ingredientDetails = new java.util.ArrayList<>();
        int minServings = Integer.MAX_VALUE;
        boolean allSufficient = true;

        for (ProductIngredient pi : productIngredients) {
            if (pi.getOptional()) {
                continue; // Skip optional ingredients for availability calculation
            }

            Ingredient ingredient = pi.getIngredient();
            BigDecimal requiredPerUnit = pi.getQuantityRequired();
            BigDecimal currentStock = ingredient.getCurrentStock();

            // Calculate max servings from this ingredient
            int maxFromIngredient = requiredPerUnit.compareTo(BigDecimal.ZERO) > 0
                    ? currentStock.divide(requiredPerUnit, 0, java.math.RoundingMode.FLOOR).intValue()
                    : Integer.MAX_VALUE;

            boolean sufficient = maxFromIngredient > 0;
            if (!sufficient) {
                allSufficient = false;
            }

            minServings = Math.min(minServings, maxFromIngredient);

            ingredientDetails.add(POSProductAvailabilityDTO.IngredientAvailability.builder()
                    .ingredientId(ingredient.getId())
                    .ingredientName(ingredient.getName())
                    .unit(ingredient.getUnit())
                    .currentStock(currentStock.doubleValue())
                    .requiredPerUnit(requiredPerUnit.doubleValue())
                    .maxServings(maxFromIngredient)
                    .sufficient(sufficient)
                    .build());
        }

        // Determine stock status
        String stockStatus;
        if (minServings == 0) {
            stockStatus = "OUT_OF_STOCK";
        } else if (minServings <= 5) {
            stockStatus = "LOW_STOCK";
        } else {
            stockStatus = "AVAILABLE";
        }

        return POSProductAvailabilityDTO.builder()
                .productId(productId)
                .productName(product.getName())
                .available(allSufficient && minServings > 0)
                .maxQuantityAvailable(minServings == Integer.MAX_VALUE ? 999 : minServings)
                .stockStatus(stockStatus)
                .ingredientDetails(ingredientDetails)
                .build();
    }

    private POSOrderResponse mapToResponse(Order order, String orderType) {
        // Handle null customer
        String customerName = null;
        String customerPhone = null;
        if (order.getCustomer() != null) {
            String firstName = order.getCustomer().getFirstName() != null ? order.getCustomer().getFirstName() : "";
            String lastName = order.getCustomer().getLastName() != null ? order.getCustomer().getLastName() : "";
            customerName = (firstName + " " + lastName).trim();
            customerPhone = order.getCustomer().getPhone();
        }

        POSOrderResponse response = POSOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .orderType(orderType)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .subtotal(order.getSubtotal())
                .tax(order.getTax())
                .deliveryFee(order.getDeliveryFee())
                .serviceFeePercent(order.getServiceFeePercent())
                .serviceFee(order.getServiceFee())
                .entryFee(order.getEntryFee())
                .total(order.getTotal())
                .orderNotes(order.getCustomerNotes())
                .createdAt(order.getCreatedAt())
                .build();

        // Map order items
        List<POSOrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> {
                    POSOrderResponse.OrderItemResponse itemResponse = new POSOrderResponse.OrderItemResponse();
                    itemResponse.setId(item.getId());
                    itemResponse.setProductName(item.getProductName());
                    itemResponse.setVariantName(item.getVariantName());
                    itemResponse.setQuantity(item.getQuantity());
                    itemResponse.setUnitPrice(item.getUnitPrice());
                    itemResponse.setPrice(item.getUnitPrice()); // backward compatibility
                    itemResponse.setTotalPrice(item.getTotalPrice());
                    itemResponse.setNotes(item.getSpecialInstructions());

                    // Parse modifiers from addOns field
                    if (item.getAddOns() != null && !item.getAddOns().isEmpty()) {
                        List<String> modifiers = List.of(item.getAddOns().split(", "));
                        itemResponse.setModifiers(modifiers);
                    }

                    return itemResponse;
                })
                .collect(Collectors.toList());
        response.setItems(itemResponses);

        // Add delivery address if applicable
        if ("DELIVERY".equals(orderType) && order.getDeliveryInfo() != null) {
            DeliveryInfo deliveryInfo = order.getDeliveryInfo();
            POSOrderResponse.DeliveryAddressResponse deliveryAddress = POSOrderResponse.DeliveryAddressResponse.builder()
                    .street(deliveryInfo.getAddress())
                    .city(deliveryInfo.getCity())
                    .state(deliveryInfo.getState())
                    .zipCode(deliveryInfo.getZipCode())
                    .deliveryInstructions(deliveryInfo.getDeliveryInstructions())
                    .build();
            response.setDeliveryAddress(deliveryAddress);
            response.setEstimatedDeliveryTime(deliveryInfo.getEstimatedDeliveryTime());
        }

        // Add dine-in info if applicable
        if ("DINE_IN".equals(orderType) && (order.getTableIds() != null || order.getDiningTable() != null)) {
            String tableNumber = "";
            List<Long> tableIdList = null;

            if (order.getTableIds() != null && !order.getTableIds().isBlank()) {
                tableIdList = java.util.Arrays.stream(order.getTableIds().split(","))
                        .map(String::trim)
                        .map(Long::parseLong)
                        .collect(Collectors.toList());

                // Get table numbers for all tables in the list
                if (!tableIdList.isEmpty()) {
                    List<RestaurantTable> tables = restaurantTableRepository.findAllById(tableIdList);
                    tableNumber = tables.stream()
                            .map(RestaurantTable::getTableNumber)
                            .collect(Collectors.joining(", "));
                }
            }

            // Fallback to diningTable if tableIds didn't provide table number
            if (tableNumber.isEmpty() && order.getDiningTable() != null) {
                tableNumber = order.getDiningTable().getTableNumber();
                if (tableIdList == null) {
                    tableIdList = List.of(order.getDiningTable().getId());
                }
            }

            POSOrderResponse.DineInInfoResponse dineInInfo = POSOrderResponse.DineInInfoResponse.builder()
                    .tableNumber(tableNumber)
                    .tableIds(tableIdList)
                    .guestCount(order.getGuestCount())
                    .build();
            response.setDineInInfo(dineInInfo);
        }

        return response;
    }

    /**
     * Get kitchen status for an order
     */
    @Transactional(readOnly = true)
    public POSKitchenStatusDTO getKitchenStatus(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        KitchenOrder kitchenOrder = kitchenOrderRepository.findByOrderId(orderId)
                .orElse(null);

        POSKitchenStatusDTO.POSKitchenStatusDTOBuilder builder = POSKitchenStatusDTO.builder()
                .orderId(orderId)
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getStatus().name());

        if (kitchenOrder != null) {
            builder.kitchenOrderId(kitchenOrder.getId())
                    .kitchenStatus(kitchenOrder.getStatus().name())
                    .priority(kitchenOrder.getPriority().name())
                    .assignedChef(kitchenOrder.getAssignedChef())
                    .preparationStartedAt(kitchenOrder.getPreparationStartedAt())
                    .preparationCompletedAt(kitchenOrder.getPreparationCompletedAt())
                    .estimatedMinutes(kitchenOrder.getEstimatedPreparationTimeMinutes())
                    .actualMinutes(kitchenOrder.getActualPreparationTimeMinutes());
        } else {
            builder.kitchenStatus("NOT_SENT");
        }

        return builder.build();
    }

    // ==================== ORDER MODIFICATION METHODS ====================

    /**
     * Get open dine-in orders for a restaurant (orders that can be modified)
     */
    @Transactional(readOnly = true)
    public List<POSOrderResponse> getOpenDineInOrders(Long restaurantId) {
        log.info("Getting open dine-in orders for restaurant: {}", restaurantId);

        List<Order> orders = orderRepository.findByRestaurant_IdAndDiningTableIsNotNullAndStatusIn(
                restaurantId,
                List.of(OrderStatus.PENDING, OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)
        );

        log.info("Found {} open dine-in orders for restaurant {}", orders.size(), restaurantId);

        return orders.stream()
                .map(order -> mapToResponse(order, "DINE_IN"))
                .collect(Collectors.toList());
    }

    /**
     * Get order by ID for modification
     */
    @Transactional(readOnly = true)
    public POSOrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        String orderType = order.getDiningTable() != null ? "DINE_IN" :
                (order.getDeliveryInfo() != null ? "DELIVERY" : "TAKEAWAY");

        return mapToResponse(order, orderType);
    }

    /**
     * Add item to an existing order
     */
    @Transactional
    public POSOrderResponse addItemToOrder(Long orderId, ModifyOrderItemRequest request) {
        log.info("Adding item to order: {} product: {}", orderId, request.getProductId());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Check order status allows modification
        if (!canModifyOrder(order)) {
            throw new IllegalStateException("Order cannot be modified in status: " + order.getStatus());
        }

        // Get product
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + request.getProductId()));

        // Check inventory availability
        List<String> missingIngredients = inventoryService.getMissingIngredients(
                request.getProductId(), request.getQuantity());
        if (!missingIngredients.isEmpty()) {
            throw new IllegalStateException("Insufficient inventory: " + String.join("; ", missingIngredients));
        }

        // Create order item
        OrderItem newItem = new OrderItem();
        newItem.setOrder(order);
        newItem.setProductId(product.getId());
        newItem.setProductName(product.getName());
        newItem.setQuantity(request.getQuantity());

        BigDecimal price = request.getPrice() != null ? request.getPrice() : product.getPrice();
        newItem.setUnitPrice(price);
        newItem.setTotalPrice(price.multiply(BigDecimal.valueOf(request.getQuantity())));
        newItem.setSpecialInstructions(request.getNotes());

        // Add modifiers
        if (request.getModifiers() != null && !request.getModifiers().isEmpty()) {
            String modifiers = request.getModifiers().stream()
                    .map(m -> m.getName() + (m.getPrice() != null && m.getPrice().compareTo(BigDecimal.ZERO) > 0 ?
                            " (+$" + m.getPrice() + ")" : ""))
                    .collect(Collectors.joining(", "));
            newItem.setAddOns(modifiers);

            // Add modifier prices to item total
            BigDecimal modifierTotal = request.getModifiers().stream()
                    .filter(m -> m.getPrice() != null)
                    .map(ModifyOrderItemRequest.ModifierInfo::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            newItem.setTotalPrice(newItem.getTotalPrice().add(modifierTotal.multiply(BigDecimal.valueOf(request.getQuantity()))));
        }

        order.addItem(newItem);

        // Recalculate totals
        recalculateOrderTotals(order);

        // Save order
        Order savedOrder = orderRepository.save(order);

        // Note: Inventory deduction for individual items is handled at order completion
        // The full order deduction happens via deductIngredientsForOrder()
        log.info("Item added to order: {}", orderId);

        String orderType = savedOrder.getDiningTable() != null ? "DINE_IN" :
                (savedOrder.getDeliveryInfo() != null ? "DELIVERY" : "TAKEAWAY");

        return mapToResponse(savedOrder, orderType);
    }

    /**
     * Remove item from an existing order
     */
    @Transactional
    public POSOrderResponse removeItemFromOrder(Long orderId, Long itemId) {
        log.info("Removing item {} from order: {}", itemId, orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Check order status allows modification
        if (!canModifyOrder(order)) {
            throw new IllegalStateException("Order cannot be modified in status: " + order.getStatus());
        }

        // Find the item
        OrderItem itemToRemove = order.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item not found with ID: " + itemId));

        // Remove item from the list
        order.getItems().remove(itemToRemove);

        // Recalculate totals
        recalculateOrderTotals(order);

        // Save order
        Order savedOrder = orderRepository.save(order);

        // Note: Inventory adjustment for removed items would need to be handled separately
        log.info("Item {} removed from order: {}", itemId, orderId);

        String orderType = savedOrder.getDiningTable() != null ? "DINE_IN" :
                (savedOrder.getDeliveryInfo() != null ? "DELIVERY" : "TAKEAWAY");

        return mapToResponse(savedOrder, orderType);
    }

    /**
     * Update item quantity in an existing order
     */
    @Transactional
    public POSOrderResponse updateItemQuantity(Long orderId, Long itemId, Integer newQuantity) {
        log.info("Updating item {} quantity to {} in order: {}", itemId, newQuantity, orderId);

        if (newQuantity < 1) {
            return removeItemFromOrder(orderId, itemId);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Check order status allows modification
        if (!canModifyOrder(order)) {
            throw new IllegalStateException("Order cannot be modified in status: " + order.getStatus());
        }

        // Find the item
        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item not found with ID: " + itemId));

        int quantityDiff = newQuantity - item.getQuantity();

        if (quantityDiff > 0) {
            // Adding quantity - check inventory
            List<String> missingIngredients = inventoryService.getMissingIngredients(
                    item.getProductId(), quantityDiff);
            if (!missingIngredients.isEmpty()) {
                throw new IllegalStateException("Insufficient inventory: " + String.join("; ", missingIngredients));
            }
        }

        // Update item
        item.setQuantity(newQuantity);
        item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(newQuantity)));

        // Recalculate totals
        recalculateOrderTotals(order);

        // Save order
        Order savedOrder = orderRepository.save(order);

        // Note: Inventory adjustment for quantity changes would need to be handled separately
        log.info("Item {} quantity updated to {} in order: {}", itemId, newQuantity, orderId);

        String orderType = savedOrder.getDiningTable() != null ? "DINE_IN" :
                (savedOrder.getDeliveryInfo() != null ? "DELIVERY" : "TAKEAWAY");

        return mapToResponse(savedOrder, orderType);
    }

    private boolean canModifyOrder(Order order) {
        return order.getStatus() == OrderStatus.NEW ||
                order.getStatus() == OrderStatus.PENDING ||
                order.getStatus() == OrderStatus.ACCEPTED ||
                order.getStatus() == OrderStatus.PREPARING;
    }

    private void recalculateOrderTotals(Order order) {
        BigDecimal subtotal = order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setSubtotal(subtotal);
        // Keep existing tax rate (0 for now)
        order.setTax(BigDecimal.ZERO);

        // Recalculate service fee if percent is set
        if (order.getServiceFeePercent() != null && order.getServiceFeePercent().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal serviceFee = subtotal.multiply(order.getServiceFeePercent())
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            order.setServiceFee(serviceFee);
        }

        // Keep existing delivery fee and entry fee
        BigDecimal serviceFee = order.getServiceFee() != null ? order.getServiceFee() : BigDecimal.ZERO;
        BigDecimal entryFee = order.getEntryFee() != null ? order.getEntryFee() : BigDecimal.ZERO;
        order.setTotal(subtotal.add(order.getTax()).add(order.getDeliveryFee()).add(serviceFee).add(entryFee));
    }

    // ==================== SPLIT BILL METHODS ====================

    /**
     * Split an order's bill
     */
    @Transactional
    public SplitBillDTO.SplitBillResponse splitBill(Long orderId, SplitBillDTO request) {
        log.info("Splitting bill for order: {} mode: {}", orderId, request.getMode());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        return switch (request.getMode()) {
            case ITEMS -> splitByItems(order, request.getItemSplits());
            case EVEN -> splitEvenly(order, request.getNumPeople());
            case AMOUNT -> splitByAmount(order, request.getAmountSplits());
        };
    }

    private SplitBillDTO.SplitBillResponse splitByItems(Order order, List<SplitBillDTO.ItemSplit> itemSplits) {
        List<SplitBillDTO.BillSplit> splits = new java.util.ArrayList<>();

        for (SplitBillDTO.ItemSplit itemSplit : itemSplits) {
            List<SplitBillDTO.SplitItemInfo> splitItems = new java.util.ArrayList<>();
            BigDecimal splitTotal = BigDecimal.ZERO;

            for (Long itemId : itemSplit.getItemIds()) {
                OrderItem item = order.getItems().stream()
                        .filter(i -> i.getId().equals(itemId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

                splitItems.add(SplitBillDTO.SplitItemInfo.builder()
                        .itemId(item.getId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getTotalPrice())
                        .build());

                splitTotal = splitTotal.add(item.getTotalPrice());
            }

            splits.add(SplitBillDTO.BillSplit.builder()
                    .personNumber(itemSplit.getPersonNumber())
                    .amount(splitTotal)
                    .items(splitItems)
                    .paid(false)
                    .build());
        }

        return SplitBillDTO.SplitBillResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .mode(SplitBillDTO.SplitMode.ITEMS)
                .originalTotal(order.getTotal())
                .splits(splits)
                .build();
    }

    private SplitBillDTO.SplitBillResponse splitEvenly(Order order, Integer numPeople) {
        if (numPeople == null || numPeople < 2) {
            throw new IllegalArgumentException("Number of people must be at least 2");
        }

        BigDecimal amountPerPerson = order.getTotal().divide(
                BigDecimal.valueOf(numPeople), 2, java.math.RoundingMode.HALF_UP);

        // Handle rounding - last person pays any remainder
        BigDecimal remainder = order.getTotal().subtract(
                amountPerPerson.multiply(BigDecimal.valueOf(numPeople)));

        List<SplitBillDTO.BillSplit> splits = new java.util.ArrayList<>();
        for (int i = 1; i <= numPeople; i++) {
            BigDecimal amount = amountPerPerson;
            if (i == numPeople && remainder.compareTo(BigDecimal.ZERO) != 0) {
                amount = amount.add(remainder);
            }

            splits.add(SplitBillDTO.BillSplit.builder()
                    .personNumber(i)
                    .amount(amount)
                    .items(java.util.Collections.emptyList())
                    .paid(false)
                    .build());
        }

        return SplitBillDTO.SplitBillResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .mode(SplitBillDTO.SplitMode.EVEN)
                .originalTotal(order.getTotal())
                .splits(splits)
                .build();
    }

    private SplitBillDTO.SplitBillResponse splitByAmount(Order order, List<SplitBillDTO.AmountSplit> amountSplits) {
        // Validate total matches
        BigDecimal totalSplitAmount = amountSplits.stream()
                .map(SplitBillDTO.AmountSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalSplitAmount.compareTo(order.getTotal()) != 0) {
            throw new IllegalArgumentException("Split amounts (" + totalSplitAmount +
                    ") do not match order total (" + order.getTotal() + ")");
        }

        List<SplitBillDTO.BillSplit> splits = amountSplits.stream()
                .map(as -> SplitBillDTO.BillSplit.builder()
                        .personNumber(as.getPersonNumber())
                        .amount(as.getAmount())
                        .items(java.util.Collections.emptyList())
                        .paid(false)
                        .build())
                .collect(Collectors.toList());

        return SplitBillDTO.SplitBillResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .mode(SplitBillDTO.SplitMode.AMOUNT)
                .originalTotal(order.getTotal())
                .splits(splits)
                .build();
    }

    // ==================== SERVICE FEE METHODS ====================

    /**
     * Apply service fee to an order
     */
    @Transactional
    public POSOrderResponse applyServiceFee(Long orderId, BigDecimal serviceFeePercent) {
        log.info("Applying service fee to order {}: percent={}", orderId, serviceFeePercent);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Validate service fee percent
        if (serviceFeePercent == null || serviceFeePercent.compareTo(BigDecimal.ZERO) < 0) {
            serviceFeePercent = BigDecimal.ZERO;
        }
        if (serviceFeePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Service fee percent cannot exceed 100%");
        }

        // Set service fee percent
        order.setServiceFeePercent(serviceFeePercent);

        // Calculate service fee amount from subtotal
        BigDecimal serviceFee = BigDecimal.ZERO;
        if (serviceFeePercent.compareTo(BigDecimal.ZERO) > 0) {
            serviceFee = order.getSubtotal().multiply(serviceFeePercent)
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        }
        order.setServiceFee(serviceFee);

        // Recalculate total (include entry fee if present)
        BigDecimal entryFee = order.getEntryFee() != null ? order.getEntryFee() : BigDecimal.ZERO;
        BigDecimal total = order.getSubtotal()
                .add(order.getTax())
                .add(order.getDeliveryFee())
                .add(serviceFee)
                .add(entryFee)
                .subtract(order.getDiscount());
        order.setTotal(total);

        // Update grand total (total + tip)
        BigDecimal tipAmount = order.getTipAmount() != null ? order.getTipAmount() : BigDecimal.ZERO;
        order.setGrandTotal(total.add(tipAmount));

        Order savedOrder = orderRepository.save(order);

        log.info("Service fee applied to order {}: fee={}, newTotal={}", orderId, serviceFee, total);

        String orderType = savedOrder.getDiningTable() != null ? "DINE_IN" :
                (savedOrder.getDeliveryInfo() != null ? "DELIVERY" : "TAKEAWAY");

        return mapToResponse(savedOrder, orderType);
    }

    /**
     * Apply service fee to an order by fixed amount
     */
    @Transactional
    public POSOrderResponse applyServiceFeeAmount(Long orderId, BigDecimal serviceFeeAmount) {
        log.info("Applying service fee amount to order {}: amount={}", orderId, serviceFeeAmount);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Validate service fee amount
        if (serviceFeeAmount == null || serviceFeeAmount.compareTo(BigDecimal.ZERO) < 0) {
            serviceFeeAmount = BigDecimal.ZERO;
        }

        // Calculate percentage for display purposes
        BigDecimal serviceFeePercent = BigDecimal.ZERO;
        if (serviceFeeAmount.compareTo(BigDecimal.ZERO) > 0 && order.getSubtotal().compareTo(BigDecimal.ZERO) > 0) {
            serviceFeePercent = serviceFeeAmount.multiply(BigDecimal.valueOf(100))
                    .divide(order.getSubtotal(), 2, java.math.RoundingMode.HALF_UP);
        }

        // Set service fee amount and calculated percent
        order.setServiceFee(serviceFeeAmount);
        order.setServiceFeePercent(serviceFeePercent);

        // Recalculate total (include entry fee if present)
        BigDecimal entryFee = order.getEntryFee() != null ? order.getEntryFee() : BigDecimal.ZERO;
        BigDecimal total = order.getSubtotal()
                .add(order.getTax())
                .add(order.getDeliveryFee())
                .add(serviceFeeAmount)
                .add(entryFee)
                .subtract(order.getDiscount());
        order.setTotal(total);

        // Update grand total (total + tip)
        BigDecimal tipAmount = order.getTipAmount() != null ? order.getTipAmount() : BigDecimal.ZERO;
        order.setGrandTotal(total.add(tipAmount));

        Order savedOrder = orderRepository.save(order);

        log.info("Service fee amount applied to order {}: fee={}, newTotal={}", orderId, serviceFeeAmount, total);

        String orderType = savedOrder.getDiningTable() != null ? "DINE_IN" :
                (savedOrder.getDeliveryInfo() != null ? "DELIVERY" : "TAKEAWAY");

        return mapToResponse(savedOrder, orderType);
    }

    // ==================== ENTRY FEE METHODS ====================

    /**
     * Apply entry fee to an order
     */
    @Transactional
    public POSOrderResponse applyEntryFee(Long orderId, BigDecimal entryFeeAmount) {
        log.info("Applying entry fee to order {}: amount={}", orderId, entryFeeAmount);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Validate entry fee amount
        if (entryFeeAmount == null || entryFeeAmount.compareTo(BigDecimal.ZERO) < 0) {
            entryFeeAmount = BigDecimal.ZERO;
        }

        // Set entry fee
        order.setEntryFee(entryFeeAmount);

        // Recalculate total
        BigDecimal serviceFee = order.getServiceFee() != null ? order.getServiceFee() : BigDecimal.ZERO;
        BigDecimal total = order.getSubtotal()
                .add(order.getTax())
                .add(order.getDeliveryFee())
                .add(serviceFee)
                .add(entryFeeAmount)
                .subtract(order.getDiscount());
        order.setTotal(total);

        // Update grand total (total + tip)
        BigDecimal tipAmount = order.getTipAmount() != null ? order.getTipAmount() : BigDecimal.ZERO;
        order.setGrandTotal(total.add(tipAmount));

        Order savedOrder = orderRepository.save(order);

        log.info("Entry fee applied to order {}: fee={}, newTotal={}", orderId, entryFeeAmount, total);

        String orderType = savedOrder.getDiningTable() != null ? "DINE_IN" :
                (savedOrder.getDeliveryInfo() != null ? "DELIVERY" : "TAKEAWAY");

        return mapToResponse(savedOrder, orderType);
    }

    // ==================== CLOSE ORDER / TABLE METHODS ====================

    /**
     * Close an order and release the associated table(s)
     */
    @Transactional
    public POSOrderResponse closeOrderAndReleaseTable(Long orderId) {
        log.info("Closing order and releasing table for order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Update order status to DELIVERED/COMPLETED if not already
        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.DELIVERED);
            order.setCompletedAt(java.time.LocalDateTime.now());
        }

        // Release the primary dining table
        if (order.getDiningTable() != null) {
            RestaurantTable table = order.getDiningTable();
            table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
            restaurantTableRepository.save(table);
            log.info("Table {} marked as AVAILABLE", table.getTableNumber());
        }

        // Release any additional tables (from tableIds field)
        if (order.getTableIds() != null && !order.getTableIds().isEmpty()) {
            List<Long> tableIdList = java.util.Arrays.stream(order.getTableIds().split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            for (Long tableId : tableIdList) {
                restaurantTableRepository.findById(tableId).ifPresent(table -> {
                    table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
                    restaurantTableRepository.save(table);
                    log.info("Table {} marked as AVAILABLE", table.getTableNumber());
                });
            }
        }

        Order savedOrder = orderRepository.save(order);

        // Determine order type for response
        String orderType = order.getDiningTable() != null ? "DINE_IN" :
                (order.getDeliveryInfo() != null ? "DELIVERY" : "TAKEAWAY");

        return mapToResponse(savedOrder, orderType);
    }

    @Transactional
    public POSOrderResponse changeTable(Long orderId, Long newTableId) {
        log.info("Changing table for order {}: newTableId={}", orderId, newTableId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Validate order is a dine-in order and not closed
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
            return mapToResponse(order, "DINE_IN");
        }

        if (newTable.getStatus() == RestaurantTable.TableStatus.OCCUPIED) {
            throw new IllegalArgumentException("Table " + newTable.getTableNumber() + " is already occupied");
        }

        // Release the current table(s)
        if (order.getDiningTable() != null) {
            RestaurantTable oldTable = order.getDiningTable();
            oldTable.setStatus(RestaurantTable.TableStatus.AVAILABLE);
            restaurantTableRepository.save(oldTable);
            log.info("Released old table {}", oldTable.getTableNumber());
        }

        // Release any additional tables from tableIds
        if (order.getTableIds() != null && !order.getTableIds().isEmpty()) {
            List<Long> tableIdList = java.util.Arrays.stream(order.getTableIds().split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            for (Long tableId : tableIdList) {
                if (!tableId.equals(newTableId)) {
                    restaurantTableRepository.findById(tableId).ifPresent(table -> {
                        table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
                        restaurantTableRepository.save(table);
                        log.info("Released additional table {}", table.getTableNumber());
                    });
                }
            }
        }

        // Assign new table
        newTable.setStatus(RestaurantTable.TableStatus.OCCUPIED);
        restaurantTableRepository.save(newTable);
        order.setDiningTable(newTable);
        order.setTableIds(String.valueOf(newTableId));
        log.info("Assigned new table {} to order {}", newTable.getTableNumber(), orderId);

        Order savedOrder = orderRepository.save(order);

        return mapToResponse(savedOrder, "DINE_IN");
    }
}
