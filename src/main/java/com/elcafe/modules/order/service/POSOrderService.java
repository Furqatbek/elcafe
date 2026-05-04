package com.elcafe.modules.order.service;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.repository.KitchenOrderRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.order.dto.pos.POSKitchenStatusDTO;
import com.elcafe.modules.order.dto.pos.POSProductAvailabilityDTO;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.service.PackagingService;
import com.elcafe.modules.bundle.entity.Bundle;
import com.elcafe.modules.bundle.repository.BundleRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.dto.pos.CreatePOSOrderRequest;
import com.elcafe.modules.order.dto.pos.POSOrderResponse;
import com.elcafe.modules.promotion.entity.CouponCode;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.entity.PromotionUsage;
import com.elcafe.modules.promotion.repository.CouponCodeRepository;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import com.elcafe.modules.promotion.repository.PromotionUsageRepository;
import com.elcafe.modules.order.entity.DeliveryInfo;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.enums.PaymentStatus;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for core POS order operations.
 * Handles order creation, retrieval, and coordination of order-related activities.
 *
 * For specific operations, use the dedicated services:
 * - POSOrderItemService: Item management (add, remove, update)
 * - POSSplitBillService: Bill splitting operations
 * - POSTableService: Table management (assign, change, release)
 * - POSOrderFeeService: Service fee and entry fee operations
 * - POSOrderDiscountService: Discount and coupon operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class POSOrderService {

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final BundleRepository bundleRepository;
    private final NotificationService notificationService;
    private final InventoryService inventoryService;
    private final InventoryProductIngredientRepository productIngredientRepository;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final DailyOrderSequenceService dailyOrderSequenceService;
    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final CouponCodeRepository couponCodeRepository;
    private final POSTableService posTableService;
    private final PackagingService packagingService;
    private final com.elcafe.modules.pos.shift.service.ShiftManagementService shiftManagementService;
    private final com.elcafe.modules.pos.shift.service.ShiftEnforcementService shiftEnforcementService;

    @Transactional
    public POSOrderResponse createOrder(CreatePOSOrderRequest request) {
        // Enforce active shift for operators/waiters (admins exempt)
        shiftEnforcementService.requireActiveShift();

        log.info("Creating POS order: type={}, restaurant={}", request.getOrderType(), request.getRestaurantId());

        // Enforce active shift — get current user and check for active shift
        Long shiftId = null;
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.elcafe.security.UserPrincipal userPrincipal) {
                com.elcafe.modules.pos.shift.entity.EmployeeShift activeShift =
                        shiftManagementService.getActiveShiftForUser(userPrincipal.getId());
                if (activeShift != null) {
                    shiftId = activeShift.getId();
                    log.info("Order linked to shift {} for user {}", shiftId, userPrincipal.getUsername());
                } else {
                    log.warn("No active shift for user {} — order created without shift link", userPrincipal.getUsername());
                }
            }
        } catch (Exception e) {
            log.warn("Could not determine active shift: {}", e.getMessage());
        }

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
        order.setShiftId(shiftId);
        order.setOrderType(OrderType.valueOf(request.getOrderType().name()));
        order.setCustomerNotes(request.getOrderNotes());

        // Set pricing - default all fees to 0
        order.setSubtotal(request.getSubtotal() != null ? request.getSubtotal() : BigDecimal.ZERO);
        order.setTax(request.getTax() != null ? request.getTax() : BigDecimal.ZERO);
        order.setDeliveryFee(request.getDeliveryFee() != null ? request.getDeliveryFee() : BigDecimal.ZERO);
        order.setServiceFeePercent(request.getServiceFeePercent() != null ? request.getServiceFeePercent() : BigDecimal.ZERO);
        order.setServiceFee(request.getServiceFee() != null ? request.getServiceFee() : BigDecimal.ZERO);
        order.setEntryFee(request.getEntryFee() != null ? request.getEntryFee() : BigDecimal.ZERO);
        order.setTotal(request.getTotal() != null ? request.getTotal() : BigDecimal.ZERO);

        // Set discount information if provided (from coupon/promotion/happy hour)
        order.setDiscount(request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO);
        order.setDiscountType(request.getDiscountType());
        order.setCouponCode(request.getCouponCode());
        order.setPromotionId(request.getPromotionId());
        order.setPromotionName(request.getPromotionName());

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
                order.setGuestCount(request.getDineInInfo().getGuestCount());

                List<Long> tableIds = request.getDineInInfo().getTableIds();
                if (tableIds != null && !tableIds.isEmpty()) {
                    posTableService.assignTablesToOrder(order, tableIds);
                }
            }
        }

        // Auto-add packaging items for delivery/takeaway
        if (order.getOrderType() == OrderType.DELIVERY || order.getOrderType() == OrderType.TAKEAWAY) {
            List<OrderItem> packagingItems = packagingService.getPackagingItems(orderItems, order.getOrderType());
            for (OrderItem pi : packagingItems) {
                pi.setOrder(order);
                order.addItem(pi);
            }
        }

        // Check ingredient availability before saving
        Order savedOrder = orderRepository.save(order);

        // Check inventory availability
        if (!inventoryService.checkIngredientAvailability(savedOrder)) {
            List<String> allMissing = new java.util.ArrayList<>();
            for (OrderItem item : savedOrder.getItems()) {
                List<String> missing = inventoryService.getMissingIngredients(
                        item.getProductId(), item.getQuantity());
                allMissing.addAll(missing);
            }
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

        // Record promotion usage for analytics if a promotion/coupon was applied
        recordPromotionUsageIfApplicable(savedOrder, request);

        // Auto-create payment if paymentMethod provided — one-call order + pay
        if (request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank()) {
            try {
                PaymentMethod method = PaymentMethod.valueOf(request.getPaymentMethod().toUpperCase());
                com.elcafe.modules.order.entity.Payment payment = com.elcafe.modules.order.entity.Payment.builder()
                        .order(savedOrder)
                        .method(method)
                        .amount(savedOrder.getTotal())
                        .amountTendered(request.getAmountTendered())
                        .changeDue(request.getChangeDue())
                        .status(PaymentStatus.COMPLETED)
                        .build();
                savedOrder.addPayment(payment);
                savedOrder.setPaymentStatus(PaymentStatus.COMPLETED);
                savedOrder = orderRepository.save(savedOrder);
                log.info("Auto-payment recorded: {} {} for order {}",
                        method, savedOrder.getTotal(), savedOrder.getOrderNumber());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid payment method '{}', skipping auto-payment", request.getPaymentMethod());
            }
        }

        log.info("POS order created successfully: {}", savedOrder.getOrderNumber());

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
        if (orderType == CreatePOSOrderRequest.OrderType.DINE_IN) {
            if (customerInfo == null || customerInfo.getPhone() == null || customerInfo.getPhone().isBlank()) {
                log.info("Dine-in order without customer info - walk-in guest");
                return null;
            }
        }

        return customerRepository.findByPhone(customerInfo.getPhone())
                .orElseGet(() -> {
                    log.info("Creating new customer with phone: {}", customerInfo.getPhone());

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
        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setQuantity(itemRequest.getQuantity());
        orderItem.setUnitPrice(itemRequest.getPrice());

        // Determine effective multiplier for weight-based or portion-based items
        BigDecimal totalPrice;
        if (itemRequest.getWeightAmount() != null && itemRequest.getWeightAmount().compareTo(BigDecimal.ZERO) > 0) {
            // Weight-based: price per unit × weight × quantity
            orderItem.setWeightAmount(itemRequest.getWeightAmount());
            totalPrice = itemRequest.getPrice()
                    .multiply(itemRequest.getWeightAmount())
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
        } else if (itemRequest.getPortionMultiplier() != null && itemRequest.getPortionMultiplier().compareTo(BigDecimal.ZERO) > 0) {
            // Portion-based: price × portion multiplier × quantity
            orderItem.setPortionMultiplier(itemRequest.getPortionMultiplier());
            totalPrice = itemRequest.getPrice()
                    .multiply(itemRequest.getPortionMultiplier())
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
        } else {
            // Standard countable item
            totalPrice = itemRequest.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
        }
        orderItem.setTotalPrice(totalPrice.setScale(2, java.math.RoundingMode.HALF_UP));
        orderItem.setSpecialInstructions(itemRequest.getNotes());

        if (Boolean.TRUE.equals(itemRequest.getIsBundle()) && itemRequest.getBundleId() != null) {
            Bundle bundle = bundleRepository.findById(itemRequest.getBundleId())
                    .orElseThrow(() -> new IllegalArgumentException("Bundle not found with ID: " + itemRequest.getBundleId()));

            // Bundle items don't have a productId - set to null to avoid confusing bundles with products
            orderItem.setProductId(null);
            orderItem.setProductName(bundle.getName());
            orderItem.setBundleId(bundle.getId());
            orderItem.setBundleName(bundle.getName());
            orderItem.setIsBundle(true);

            log.info("Added bundle to order: {} - {}", bundle.getId(), bundle.getName());
        } else {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + itemRequest.getProductId()));

            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setIsBundle(false);
            // Copy weight unit from product for weight-based items
            if (orderItem.getWeightAmount() != null && product.getWeightUnit() != null) {
                orderItem.setWeightUnit(product.getWeightUnit());
            }
        }

        if (itemRequest.getModifiers() != null && !itemRequest.getModifiers().isEmpty()) {
            for (CreatePOSOrderRequest.ModifierInfo modifier : itemRequest.getModifiers()) {
                orderItem.addAddOn(
                        modifier.getAddOnId(),
                        modifier.getName(),
                        modifier.getPrice(),
                        modifier.getQuantity() != null ? modifier.getQuantity() : 1
                );
            }
            String modifiersString = itemRequest.getModifiers().stream()
                    .map(m -> m.getName() + (m.getPrice().compareTo(BigDecimal.ZERO) > 0 ? " (+$" + m.getPrice() + ")" : ""))
                    .collect(Collectors.joining(", "));
            orderItem.setAddOns(modifiersString);
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
        deliveryInfo.setEstimatedDeliveryTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(45));
        return deliveryInfo;
    }

    @Transactional(readOnly = true)
    public POSProductAvailabilityDTO getProductAvailability(Long productId, Long restaurantId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        List<ProductIngredient> productIngredients =
                productIngredientRepository.findByProductIdWithIngredients(productId);

        List<POSProductAvailabilityDTO.IngredientAvailability> ingredientDetails = new java.util.ArrayList<>();
        int minServings = Integer.MAX_VALUE;
        boolean allSufficient = true;

        for (ProductIngredient pi : productIngredients) {
            if (pi.getOptional()) {
                continue;
            }

            Ingredient ingredient = pi.getIngredient();
            BigDecimal requiredPerUnit = pi.getQuantityRequired();
            BigDecimal currentStock = ingredient.getCurrentStock();

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

    /**
     * Get the order type string from an order.
     */
    public String getOrderTypeString(Order order) {
        if (order.getOrderType() != null) {
            return order.getOrderType().name();
        }
        return order.getDiningTable() != null ? "DINE_IN" :
                (order.getDeliveryInfo() != null ? "DELIVERY" : "TAKEAWAY");
    }

    public POSOrderResponse mapToResponse(Order order, String orderType) {
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
                .restaurantId(order.getRestaurant() != null ? order.getRestaurant().getId() : null)
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
                .discount(order.getDiscount())
                .total(order.getTotal())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPayment() != null ? order.getPayment().getMethod().name() : null)
                .fullyPaid(order.isFullyPaid())
                .orderNotes(order.getCustomerNotes())
                .createdAt(order.getCreatedAt())
                .build();

        List<POSOrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> {
                    POSOrderResponse.OrderItemResponse itemResponse = new POSOrderResponse.OrderItemResponse();
                    itemResponse.setId(item.getId());
                    itemResponse.setProductName(item.getProductName());
                    itemResponse.setVariantName(item.getVariantName());
                    itemResponse.setQuantity(item.getQuantity());
                    itemResponse.setUnitPrice(item.getUnitPrice());
                    itemResponse.setPrice(item.getUnitPrice());
                    itemResponse.setTotalPrice(item.getTotalPrice());
                    itemResponse.setNotes(item.getSpecialInstructions());
                    itemResponse.setIsPackagingItem(Boolean.TRUE.equals(item.getIsPackagingItem()));

                    String addOnsDisplay = item.getAddOnsDisplay();
                    if (addOnsDisplay != null && !addOnsDisplay.isEmpty()) {
                        List<String> modifiers = List.of(addOnsDisplay.split(", "));
                        itemResponse.setModifiers(modifiers);
                    }

                    return itemResponse;
                })
                .sorted((a, b) -> {
                    String nameA = a.getProductName() != null ? a.getProductName() : "";
                    String nameB = b.getProductName() != null ? b.getProductName() : "";
                    return nameA.compareToIgnoreCase(nameB);
                })
                .collect(Collectors.toList());
        response.setItems(itemResponses);

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

        if ("DINE_IN".equals(orderType) && order.hasTables()) {
            List<Long> tableIdList = order.getTableIdList();
            String tableNumber = "";

            if (!tableIdList.isEmpty()) {
                List<RestaurantTable> tables = order.getTables();
                if (!tables.isEmpty()) {
                    tableNumber = tables.stream()
                            .map(RestaurantTable::getTableNumber)
                            .collect(Collectors.joining(", "));
                } else {
                    tables = restaurantTableRepository.findAllById(tableIdList);
                    tableNumber = tables.stream()
                            .map(RestaurantTable::getTableNumber)
                            .collect(Collectors.joining(", "));
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
                .filter(order -> order.getPaymentStatus() != PaymentStatus.COMPLETED)
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

        return mapToResponse(order, getOrderTypeString(order));
    }

    /**
     * Record promotion usage for analytics when order is created with discount info.
     */
    private void recordPromotionUsageIfApplicable(Order order, CreatePOSOrderRequest request) {
        if (order.getDiscount() == null || order.getDiscount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String discountType = order.getDiscountType();
        if (discountType == null || "MANUAL".equals(discountType) || "HAPPY_HOUR".equals(discountType)) {
            return;
        }

        try {
            Promotion promotion = null;
            CouponCode couponCode = null;

            if (request.getPromotionId() != null) {
                promotion = promotionRepository.findById(request.getPromotionId()).orElse(null);
            }

            if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
                couponCode = couponCodeRepository.findByCodeIgnoreCase(request.getCouponCode()).orElse(null);
                if (couponCode != null && promotion == null) {
                    promotion = couponCode.getPromotion();
                }
            }

            if (promotion == null) {
                log.warn("Cannot record promotion usage - no promotion found for order {} (promotionId={}, couponCode={})",
                        order.getOrderNumber(), request.getPromotionId(), request.getCouponCode());
                return;
            }

            if (promotionUsageRepository.findByOrder_Id(order.getId()).isPresent()) {
                log.debug("Promotion usage already recorded for order {}", order.getOrderNumber());
                return;
            }

            PromotionUsage usage = PromotionUsage.builder()
                    .promotion(promotion)
                    .couponCode(couponCode)
                    .customer(order.getCustomer())
                    .order(order)
                    .discountAmount(order.getDiscount())
                    .usedAt(LocalDateTime.now())
                    .build();

            promotionUsageRepository.save(usage);
            log.info("Promotion usage recorded for POS order: promotion={}, order={}, discount={}",
                    promotion.getId(), order.getOrderNumber(), order.getDiscount());

            if (couponCode != null) {
                couponCode.incrementUsage();
                couponCodeRepository.save(couponCode);
            }
        } catch (Exception e) {
            log.error("Failed to record promotion usage for order {}: {}",
                    order.getOrderNumber(), e.getMessage(), e);
        }
    }

    /**
     * Create an order from offline data (synced from POS offline mode).
     * This method parses the offline order data and creates an order.
     *
     * @param restaurantId The restaurant ID
     * @param orderData The order data as a map (from offline storage)
     * @param clientOrderId The client-side order ID
     * @param deviceId The device ID that created the order
     * @return The created Order
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Order createOrderFromOffline(Long restaurantId, java.util.Map<String, Object> orderData,
                                        String clientOrderId, String deviceId) {
        log.info("Creating order from offline data: restaurantId={}, clientOrderId={}, deviceId={}",
                restaurantId, clientOrderId, deviceId);

        // Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found with ID: " + restaurantId));

        // Create order
        Order order = new Order();
        order.setOrderNumber(dailyOrderSequenceService.generateNextOrderNumber());
        order.setRestaurant(restaurant);
        order.setStatus(OrderStatus.NEW);
        order.setClientOrderId(clientOrderId);
        order.setDeviceId(deviceId);
        order.setIsOfflineOrder(true);

        // Extract order type
        String orderTypeStr = (String) orderData.getOrDefault("orderType", "DINE_IN");
        order.setOrderType(OrderType.valueOf(orderTypeStr));

        // Extract pricing
        order.setSubtotal(getBigDecimal(orderData, "subtotal", BigDecimal.ZERO));
        order.setTax(getBigDecimal(orderData, "tax", BigDecimal.ZERO));
        order.setDeliveryFee(getBigDecimal(orderData, "deliveryFee", BigDecimal.ZERO));
        order.setServiceFee(getBigDecimal(orderData, "serviceFee", BigDecimal.ZERO));
        order.setDiscount(getBigDecimal(orderData, "discount", BigDecimal.ZERO));
        order.setTotal(getBigDecimal(orderData, "total", BigDecimal.ZERO));

        // Extract items
        java.util.List<java.util.Map<String, Object>> itemsData =
                (java.util.List<java.util.Map<String, Object>>) orderData.get("items");
        if (itemsData != null) {
            for (java.util.Map<String, Object> itemData : itemsData) {
                OrderItem item = createOrderItemFromOfflineData(itemData, order);
                order.addItem(item);
            }
        }

        // Save order
        Order savedOrder = orderRepository.save(order);

        log.info("Offline order created successfully: {} -> clientOrderId: {}",
                savedOrder.getOrderNumber(), clientOrderId);

        return savedOrder;
    }

    private OrderItem createOrderItemFromOfflineData(java.util.Map<String, Object> itemData, Order order) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductId(getLong(itemData, "productId"));
        item.setProductName((String) itemData.getOrDefault("productName", "Unknown"));
        item.setQuantity(getInteger(itemData, "quantity", 1));
        item.setUnitPrice(getBigDecimal(itemData, "unitPrice", BigDecimal.ZERO));
        item.setTotalPrice(getBigDecimal(itemData, "totalPrice", BigDecimal.ZERO));
        item.setSpecialInstructions((String) itemData.get("specialInstructions"));
        return item;
    }

    private BigDecimal getBigDecimal(java.util.Map<String, Object> data, String key, BigDecimal defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        if (value instanceof String) return new BigDecimal((String) value);
        return defaultValue;
    }

    private Long getLong(java.util.Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) return Long.valueOf((String) value);
        return null;
    }

    private Integer getInteger(java.util.Map<String, Object> data, String key, Integer defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) return Integer.valueOf((String) value);
        return defaultValue;
    }
}
