package com.elcafe.modules.selfservice.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.utils.LogSanitizer;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.enums.RegistrationSource;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.menu.entity.LinkedItem;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.LinkedItemRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.menu.service.PackagingService;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.DailyOrderSequenceService;
import com.elcafe.modules.order.service.OrderEventBroadcaster;
import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.waiter.event.OrderEventPublisher;
import com.elcafe.modules.promotion.dto.ApplyDiscountRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponResponse;
import com.elcafe.modules.promotion.enums.DiscountType;
import com.elcafe.modules.promotion.service.CouponValidationService;
import com.elcafe.modules.promotion.service.DiscountCalculationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import com.elcafe.modules.selfservice.dto.AddToCartRequest;
import com.elcafe.modules.selfservice.dto.CartItemResponse;
import com.elcafe.modules.selfservice.dto.SubmitOrderRequest;
import com.elcafe.modules.selfservice.entity.*;
import com.elcafe.modules.selfservice.enums.SelfServiceOrderType;
import com.elcafe.modules.selfservice.exception.*;
import com.elcafe.modules.selfservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for self-service ordering operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfServiceOrderService {

    private final SelfServiceSessionRepository sessionRepository;
    private final SelfServiceCartItemRepository cartItemRepository;
    private final SelfServiceOrderRepository selfServiceOrderRepository;
    private final SelfServiceSettingsRepository settingsRepository;
    private final QRCodeRepository qrCodeRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final LinkedItemRepository linkedItemRepository;
    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final CustomerRepository customerRepository;
    private final DailyOrderSequenceService dailyOrderSequenceService;
    private final com.elcafe.modules.bundle.repository.BundleRepository bundleRepository;
    private final CouponValidationService couponValidationService;
    private final DiscountCalculationService discountCalculationService;
    @Lazy private final OwnerNotificationService ownerNotificationService;
    @Lazy private final OrderEventBroadcaster orderEventBroadcaster;
    /** V184 floor map: a QR dine-in order seats a party, so the map repaints that table live. */
    @Lazy private final com.elcafe.modules.restaurant.service.FloorEventBroadcaster floorEventBroadcaster;
    @Lazy private final OrderEventPublisher orderEventPublisher;
    @Lazy private final NotificationService notificationService;
    private final PackagingService packagingService;

    /**
     * Session expiry time in hours.
     * 4 hours provides enough time for browsing menu and completing orders
     * while limiting orphaned session accumulation.
     */
    private static final int SESSION_EXPIRY_HOURS = 24;

    /**
     * Phone validation pattern.
     * Allows digits, spaces, dashes, parentheses, and optional + prefix.
     * Requires 7-20 characters to support international formats.
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[\\d\\s\\-()]{7,20}$");

    /** Maximum length for customer notes to prevent DoS via large payloads. */
    private static final int MAX_NOTES_LENGTH = 500;

    /** Maximum length for customer name. */
    private static final int MAX_NAME_LENGTH = 100;

    /**
     * Start a new self-service session by scanning QR code.
     */
    @Transactional
    public SelfServiceSession startSession(String qrCode, String deviceInfo, String ipAddress) {
        log.info("Starting self-service session: qrCode={}, ip={}", qrCode, ipAddress);

        QRCode qr = qrCodeRepository.findByCode(qrCode)
                .orElseThrow(() -> {
                    log.warn("Invalid QR code scanned: {}", qrCode);
                    return new SelfServiceException("Invalid QR code");
                });

        if (!qr.isValid()) {
            log.warn("Expired/inactive QR code used: {}", qrCode);
            throw new SelfServiceException("QR code is expired or inactive");
        }

        // Check if self-service is enabled
        SelfServiceSettings settings = settingsRepository.findByRestaurantId(qr.getRestaurant().getId())
                .orElse(null);
        if (settings == null || !settings.getEnabled()) {
            throw new BadRequestException("Self-service ordering is not enabled for this restaurant");
        }

        // Record the scan
        qr.recordScan();
        qrCodeRepository.save(qr);

        // Create new session
        SelfServiceSession session = SelfServiceSession.builder()
                .sessionToken(UUID.randomUUID().toString())
                .restaurant(qr.getRestaurant())
                .table(qr.getTable())
                .qrCode(qr)
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .expiresAt(LocalDateTime.now().plusHours(SESSION_EXPIRY_HOURS))
                .build();

        return sessionRepository.save(session);
    }

    /**
     * Start a new self-service session for takeaway orders (no QR code required).
     */
    @Transactional
    public SelfServiceSession startTakeawaySession(Long restaurantId, String deviceInfo, String ipAddress) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        // Check if self-service is enabled and takeaway is allowed
        SelfServiceSettings settings = settingsRepository.findByRestaurantId(restaurantId)
                .orElse(null);
        if (settings == null || !settings.getEnabled()) {
            throw new BadRequestException("Self-service ordering is not enabled for this restaurant");
        }
        if (!Boolean.TRUE.equals(settings.getAllowTakeaway())) {
            throw new BadRequestException("Takeaway orders are not enabled for this restaurant");
        }

        // Create new session without table (takeaway)
        SelfServiceSession session = SelfServiceSession.builder()
                .sessionToken(UUID.randomUUID().toString())
                .restaurant(restaurant)
                .table(null) // No table for takeaway
                .qrCode(null) // No QR code for direct takeaway access
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .expiresAt(LocalDateTime.now().plusHours(SESSION_EXPIRY_HOURS))
                .build();

        return sessionRepository.save(session);
    }

    /**
     * Get session by token.
     */
    @Transactional(readOnly = true)
    public Optional<SelfServiceSession> getSession(String sessionToken) {
        return sessionRepository.findBySessionTokenAndIsActiveTrue(sessionToken);
    }

    /**
     * Add item to cart.
     */
    @Transactional
    public SelfServiceCartItem addToCart(String sessionToken, AddToCartRequest request) {
        SelfServiceSession session = getValidSession(sessionToken);

        log.debug("Adding to cart: sessionId={}, productId={}, bundleId={}, qty={}",
                session.getId(), request.getProductId(), request.getBundleId(), request.getQuantity());

        // Handle bundle
        if (Boolean.TRUE.equals(request.getIsBundle()) && request.getBundleId() != null) {
            return addBundleToCart(session, request);
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> {
                    log.warn("Product not found: {}", request.getProductId());
                    return new ProductNotFoundException(request.getProductId());
                });

        // Check if product belongs to session's restaurant
        if (!product.getCategory().getRestaurant().getId().equals(session.getRestaurant().getId())) {
            log.warn("Product {} not available at restaurant {}", request.getProductId(), session.getRestaurant().getId());
            throw new CartOperationException("addToCart", request.getProductId(), "Product not available at this restaurant");
        }

        ProductVariant variant = null;
        BigDecimal unitPrice = product.getPrice();

        if (request.getVariantId() != null) {
            variant = variantRepository.findById(request.getVariantId())
                    .orElseThrow(() -> new CartOperationException("addToCart", "Variant not found: " + request.getVariantId()));
            unitPrice = variant.getPrice();
        }

        // Check if same item already in cart (update quantity)
        Optional<SelfServiceCartItem> existingItem = cartItemRepository
                .findBySessionIdAndProductIdAndVariantId(session.getId(), product.getId(),
                        variant != null ? variant.getId() : null);

        if (existingItem.isPresent() && request.getSpecialInstructions() == null) {
            SelfServiceCartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            return cartItemRepository.save(item);
        }

        // Create new cart item
        SelfServiceCartItem cartItem = SelfServiceCartItem.builder()
                .session(session)
                .product(product)
                .variant(variant)
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .specialInstructions(request.getSpecialInstructions())
                .isBundle(false)
                .build();

        cartItem = cartItemRepository.save(cartItem);

        // Add modifiers - batch fetch to avoid N+1 queries
        if (request.getModifiers() != null && !request.getModifiers().isEmpty()) {
            List<Long> linkedItemIds = request.getModifiers().stream()
                    .map(AddToCartRequest.ModifierRequest::getLinkedItemId)
                    .toList();

            List<LinkedItem> linkedItems = linkedItemRepository.findAllByIdWithLinkedProduct(linkedItemIds);

            // Create a map for quick lookup
            java.util.Map<Long, LinkedItem> linkedItemMap = linkedItems.stream()
                    .collect(java.util.stream.Collectors.toMap(LinkedItem::getId, li -> li));

            for (AddToCartRequest.ModifierRequest modReq : request.getModifiers()) {
                LinkedItem linkedItem = linkedItemMap.get(modReq.getLinkedItemId());
                if (linkedItem == null) {
                    throw new ResourceNotFoundException("Modifier not found: " + modReq.getLinkedItemId());
                }

                if (linkedItem.getLinkedProduct() == null) {
                    throw new ResourceNotFoundException("Modifier product not found for linked item: " + linkedItem.getId());
                }

                SelfServiceCartModifier modifier = SelfServiceCartModifier.builder()
                        .cartItem(cartItem)
                        .linkedItem(linkedItem)
                        .quantity(modReq.getQuantity())
                        .price(linkedItem.getLinkedProduct().getPrice())
                        .build();
                cartItem.getModifiers().add(modifier);
            }
            cartItem = cartItemRepository.save(cartItem);
        }

        session.touch();
        sessionRepository.save(session);

        return cartItem;
    }

    /**
     * Add bundle to cart.
     */
    private SelfServiceCartItem addBundleToCart(SelfServiceSession session, AddToCartRequest request) {
        log.debug("Adding bundle to cart: sessionId={}, bundleId={}", session.getId(), request.getBundleId());

        var bundle = bundleRepository.findById(request.getBundleId())
                .orElseThrow(() -> {
                    log.warn("Bundle not found: {}", request.getBundleId());
                    return new ProductNotFoundException(request.getBundleId(), true);
                });

        // Check if bundle belongs to session's restaurant
        if (!bundle.getRestaurant().getId().equals(session.getRestaurant().getId())) {
            log.warn("Bundle {} not available at restaurant {}", request.getBundleId(), session.getRestaurant().getId());
            throw new CartOperationException("addBundleToCart", request.getBundleId(), "Bundle not available at this restaurant");
        }

        // Check if bundle is active and currently available
        if (!bundle.isCurrentlyAvailable()) {
            log.info("Bundle {} is not currently available", request.getBundleId());
            throw new CartOperationException("addBundleToCart", request.getBundleId(), "Bundle is not currently available");
        }

        // Check if same bundle already in cart (update quantity)
        Optional<SelfServiceCartItem> existingItem = cartItemRepository
                .findBySessionIdAndBundleId(session.getId(), bundle.getId());

        if (existingItem.isPresent()) {
            SelfServiceCartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            return cartItemRepository.save(item);
        }

        // Create new cart item for bundle
        SelfServiceCartItem cartItem = SelfServiceCartItem.builder()
                .session(session)
                .bundleId(bundle.getId())
                .bundleName(bundle.getName())
                .isBundle(true)
                .quantity(request.getQuantity())
                .unitPrice(bundle.getBundlePrice())
                .specialInstructions(request.getSpecialInstructions())
                .build();

        cartItem = cartItemRepository.save(cartItem);

        session.touch();
        sessionRepository.save(session);

        return cartItem;
    }

    /**
     * Update cart item quantity.
     */
    @Transactional
    public SelfServiceCartItem updateCartItem(String sessionToken, Long cartItemId, Integer quantity) {
        SelfServiceSession session = getValidSession(sessionToken);

        SelfServiceCartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (!item.getSession().getId().equals(session.getId())) {
            log.warn("Cart item {} does not belong to session {}", cartItemId, session.getId());
            throw new CartOperationException("updateQuantity", cartItemId, "Cart item not found");
        }

        if (quantity <= 0) {
            log.debug("Removing cart item {} (quantity set to {})", cartItemId, quantity);
            cartItemRepository.delete(item);
            return null;
        }

        log.debug("Updating cart item {} quantity: {} -> {}", cartItemId, item.getQuantity(), quantity);
        item.setQuantity(quantity);
        session.touch();
        sessionRepository.save(session);

        return cartItemRepository.save(item);
    }

    /**
     * Remove item from cart.
     */
    @Transactional
    public void removeFromCart(String sessionToken, Long cartItemId) {
        SelfServiceSession session = getValidSession(sessionToken);

        log.debug("Removing from cart: sessionId={}, cartItemId={}", session.getId(), cartItemId);

        SelfServiceCartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> {
                    log.debug("Cart item not found: {}", cartItemId);
                    return new CartOperationException("removeFromCart", cartItemId, "Cart item not found");
                });

        if (!item.getSession().getId().equals(session.getId())) {
            log.warn("Cart item {} does not belong to session {}", cartItemId, session.getId());
            throw new CartOperationException("removeFromCart", cartItemId, "Cart item not found");
        }

        cartItemRepository.delete(item);
        session.touch();
        sessionRepository.save(session);
        log.debug("Cart item {} removed successfully", cartItemId);
    }

    /**
     * Get cart contents.
     */
    @Transactional(readOnly = true)
    public List<CartItemResponse> getCart(String sessionToken) {
        SelfServiceSession session = getValidSession(sessionToken);
        List<SelfServiceCartItem> items = cartItemRepository.findBySessionIdOrderByAddedAtAsc(session.getId());

        return items.stream().map(this::toCartItemResponse).toList();
    }

    /**
     * Clear cart.
     */
    @Transactional
    public void clearCart(String sessionToken) {
        SelfServiceSession session = getValidSession(sessionToken);
        cartItemRepository.deleteAllBySessionId(session.getId());
        session.touch();
        sessionRepository.save(session);
    }

    /**
     * Submit order from cart.
     */
    @Transactional
    public SelfServiceOrder submitOrder(String sessionToken, SubmitOrderRequest request) {
        SelfServiceSession session = getValidSession(sessionToken);
        log.info("Submitting order: sessionId={}, orderType={}", session.getId(), request.getOrderType());

        validateOrderRequest(request);

        List<SelfServiceCartItem> cartItems = cartItemRepository.findBySessionIdOrderByAddedAtAsc(session.getId());
        if (cartItems.isEmpty()) {
            log.info("Order submission failed: empty cart for session {}", session.getId());
            throw OrderSubmissionException.emptyCart();
        }

        SelfServiceSettings settings = settingsRepository.findByRestaurantId(session.getRestaurant().getId())
                .orElseThrow(() -> {
                    log.error("Self-service not configured for restaurant: {}", session.getRestaurant().getId());
                    return OrderSubmissionException.serviceNotConfigured();
                });

        BigDecimal subtotal = calculateSubtotal(cartItems);
        validateMinimumOrderAmount(subtotal, settings);

        Customer customer = findOrCreateCustomer(request, session.getRestaurant().getId());
        String customerNotes = sanitizeTextInput(
                request.getNotes() != null ? request.getNotes() : request.getSpecialInstructions(),
                MAX_NOTES_LENGTH
        );

        OrderType orderType = mapOrderType(request.getOrderType());
        RestaurantTable diningTable = fetchDiningTable(session, orderType);

        Order order = createOrder(session, customer, diningTable, orderType, subtotal, customerNotes, settings);
        List<OrderItem> orderItems = convertCartItemsToOrderItems(cartItems, order);
        order.setItems(new ArrayList<>(orderItems));

        // Auto-add packaging items for takeaway
        if (orderType == OrderType.TAKEAWAY) {
            List<OrderItem> packagingItems = packagingService.getPackagingItems(orderItems, orderType);
            for (OrderItem pi : packagingItems) {
                pi.setOrder(order);
                order.getItems().add(pi);
            }
            if (!packagingItems.isEmpty()) {
                // Recalculate subtotal with packaging
                BigDecimal packagingTotal = packagingItems.stream()
                        .map(OrderItem::getTotalPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                subtotal = subtotal.add(packagingTotal);
                order.setSubtotal(subtotal);
                order.setTotal(subtotal);
            }
        }

        applyCouponIfProvided(request, order, session, customer, subtotal, orderType, orderItems);
        recalculateTotalAfterDiscount(order, subtotal);

        order = orderRepository.save(order);

        // Create SelfServiceOrder BEFORE sending notifications to ensure atomicity
        // If this fails, the entire transaction rolls back including the main order
        SelfServiceOrder ssOrder = createSelfServiceOrder(order, session, request, settings);

        updateTableStatusIfDineIn(orderType, diningTable, order);
        finalizeOrderSubmission(session, request, customer);

        // Send notifications AFTER all database operations complete successfully
        // These are fire-and-forget operations that should not affect the transaction
        sendOrderNotifications(order);

        log.info("Self-service order created: {} for session {}", order.getId(), session.getId());
        return ssOrder;
    }

    private void validateOrderRequest(SubmitOrderRequest request) {
        if (request.getOrderType() == SelfServiceOrderType.TAKEAWAY) {
            if (request.getCustomerName() == null || request.getCustomerName().trim().isEmpty()) {
                throw OrderSubmissionException.missingCustomerDetails("Customer name");
            }
            if (request.getCustomerPhone() == null || request.getCustomerPhone().trim().isEmpty()) {
                throw OrderSubmissionException.missingCustomerDetails("Customer phone");
            }
        }

        if (request.getCustomerPhone() != null && !request.getCustomerPhone().trim().isEmpty()) {
            if (!isValidPhoneNumber(request.getCustomerPhone().trim())) {
                throw OrderSubmissionException.invalidPhone();
            }
        }

        if (request.getCustomerName() != null && request.getCustomerName().length() > MAX_NAME_LENGTH) {
            throw new OrderSubmissionException("VALIDATION_ERROR", "Customer name exceeds maximum length of " + MAX_NAME_LENGTH);
        }
    }

    private BigDecimal calculateSubtotal(List<SelfServiceCartItem> cartItems) {
        return cartItems.stream()
                .map(SelfServiceCartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateMinimumOrderAmount(BigDecimal subtotal, SelfServiceSettings settings) {
        if (subtotal.compareTo(settings.getMinimumOrderAmount()) < 0) {
            log.info("Order submission failed: minimum not met. Required={}, Got={}", settings.getMinimumOrderAmount(), subtotal);
            throw OrderSubmissionException.minimumNotMet(settings.getMinimumOrderAmount(), subtotal);
        }
    }

    private Customer findOrCreateCustomer(SubmitOrderRequest request, Long restaurantId) {
        if (request.getCustomerPhone() == null || request.getCustomerPhone().isEmpty()) {
            return null;
        }

        String phone = request.getCustomerPhone().trim();
        String customerName = request.getCustomerName() != null ? request.getCustomerName().trim() : "";

        return customerRepository.findByPhoneAndRestaurantId(phone, restaurantId)
                .map(existingCustomer -> {
                    if (!customerName.isEmpty() &&
                        (existingCustomer.getFirstName() == null || existingCustomer.getFirstName().isEmpty()
                         || "Customer".equals(existingCustomer.getFirstName()))) {
                        existingCustomer.setFirstName(customerName);
                        return customerRepository.save(existingCustomer);
                    }
                    return existingCustomer;
                })
                .orElseGet(() -> {
                    Customer newCustomer = Customer.builder()
                            .restaurantId(restaurantId)
                            .phone(phone)
                            .firstName(customerName.isEmpty() ? "Customer" : customerName)
                            .lastName("")
                            .registrationSource(RegistrationSource.QR_ORDER)
                            .active(true)
                            .build();
                    log.info("Creating new customer from self-service order: phone={}, restaurant={}", LogSanitizer.phone(phone), restaurantId);
                    return customerRepository.save(newCustomer);
                });
    }

    private OrderType mapOrderType(SelfServiceOrderType selfServiceOrderType) {
        return selfServiceOrderType == SelfServiceOrderType.TAKEAWAY ? OrderType.TAKEAWAY : OrderType.DINE_IN;
    }

    private RestaurantTable fetchDiningTable(SelfServiceSession session, OrderType orderType) {
        if (orderType != OrderType.DINE_IN || session.getTable() == null) {
            return null;
        }

        RestaurantTable diningTable = restaurantTableRepository.findById(session.getTable().getId()).orElse(null);
        log.info("Setting dining table for self-service dine-in order: tableId={}, tableNumber={}",
                diningTable != null ? diningTable.getId() : null,
                diningTable != null ? diningTable.getTableNumber() : null);
        return diningTable;
    }

    private Order createOrder(SelfServiceSession session, Customer customer, RestaurantTable diningTable,
                              OrderType orderType, BigDecimal subtotal, String customerNotes, SelfServiceSettings settings) {
        return Order.builder()
                .orderNumber(dailyOrderSequenceService.generateNextOrderNumber())
                .restaurant(session.getRestaurant())
                .customer(customer)
                .diningTable(diningTable)
                .orderType(orderType)
                .status(Boolean.TRUE.equals(settings.getAutoAcceptOrders()) ? OrderStatus.ACCEPTED : OrderStatus.PENDING)
                .subtotal(subtotal)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .total(subtotal)
                .customerNotes(customerNotes)
                .orderSource(OrderSource.SELF_SERVICE)
                .placedAt(OffsetDateTime.now())
                .build();
    }

    private List<OrderItem> convertCartItemsToOrderItems(List<SelfServiceCartItem> cartItems, Order order) {
        List<OrderItem> orderItems = new ArrayList<>();
        for (SelfServiceCartItem cartItem : cartItems) {
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .quantity(cartItem.getQuantity())
                    .unitPrice(cartItem.getUnitPrice())
                    .totalPrice(cartItem.getTotalPrice())
                    .specialInstructions(cartItem.getSpecialInstructions())
                    .build();

            if (Boolean.TRUE.equals(cartItem.getIsBundle()) && cartItem.getBundleId() != null) {
                orderItem.setProductId(null);
                orderItem.setProductName(cartItem.getBundleName());
                orderItem.setBundleId(cartItem.getBundleId());
                orderItem.setBundleName(cartItem.getBundleName());
                orderItem.setIsBundle(true);
            } else {
                orderItem.setProductId(cartItem.getProduct().getId());
                orderItem.setProductName(cartItem.getProduct().getName());
                orderItem.setVariantId(cartItem.getVariant() != null ? cartItem.getVariant().getId() : null);
                orderItem.setVariantName(cartItem.getVariant() != null ? cartItem.getVariant().getName() : null);
                orderItem.setIsBundle(false);
            }

            // Transfer cart modifiers/add-ons to order item
            if (cartItem.getModifiers() != null && !cartItem.getModifiers().isEmpty()) {
                for (SelfServiceCartModifier modifier : cartItem.getModifiers()) {
                    String modifierName = modifier.getLinkedItem() != null
                            && modifier.getLinkedItem().getLinkedProduct() != null
                            ? modifier.getLinkedItem().getLinkedProduct().getName()
                            : "Add-on";
                    Long addOnId = modifier.getLinkedItem() != null
                            ? modifier.getLinkedItem().getId()
                            : null;
                    orderItem.addAddOn(
                            addOnId,
                            modifierName,
                            modifier.getPrice() != null ? modifier.getPrice() : BigDecimal.ZERO,
                            modifier.getQuantity() != null ? modifier.getQuantity() : 1
                    );
                }
            }

            orderItems.add(orderItem);
        }
        return orderItems;
    }

    private void applyCouponIfProvided(SubmitOrderRequest request, Order order, SelfServiceSession session,
                                        Customer customer, BigDecimal subtotal, OrderType orderType, List<OrderItem> orderItems) {
        if (request.getCouponCode() == null || request.getCouponCode().isBlank()) {
            return;
        }

        try {
            ValidateCouponRequest validateRequest = ValidateCouponRequest.builder()
                    .code(request.getCouponCode())
                    .restaurantId(session.getRestaurant().getId())
                    .customerId(customer != null ? customer.getId() : null)
                    .orderSubtotal(subtotal)
                    .orderType(orderType.name())
                    .items(orderItems.stream()
                            .map(item -> ValidateCouponRequest.OrderItemInfo.builder()
                                    .productId(item.getProductId())
                                    .quantity(item.getQuantity())
                                    .price(item.getUnitPrice())
                                    .build())
                            .toList())
                    .build();

            ValidateCouponResponse couponResponse = couponValidationService.validateCoupon(validateRequest);

            if (Boolean.TRUE.equals(couponResponse.getValid())) {
                ApplyDiscountRequest discountRequest = ApplyDiscountRequest.builder()
                        .couponCode(request.getCouponCode())
                        .discountType(DiscountType.COUPON)
                        .build();
                discountCalculationService.applyDiscount(order, discountRequest);
                log.info("Coupon {} applied to self-service order: discount={}", request.getCouponCode(), order.getDiscount());
            } else {
                log.warn("Invalid coupon code {} for self-service order: {}", request.getCouponCode(), couponResponse.getErrorMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to apply coupon {} for self-service order: {}", request.getCouponCode(), e.getMessage());
        }
    }

    private void recalculateTotalAfterDiscount(Order order, BigDecimal subtotal) {
        BigDecimal discount = order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO;
        order.setTotal(subtotal.subtract(discount));
    }

    private void updateTableStatusIfDineIn(OrderType orderType, RestaurantTable diningTable, Order order) {
        if (orderType != OrderType.DINE_IN || diningTable == null) {
            return;
        }

        RestaurantTable.TableStatus previousStatus = diningTable.getStatus();
        diningTable.setStatus(RestaurantTable.TableStatus.OCCUPIED);
        restaurantTableRepository.save(diningTable);
        log.info("Table {} marked as OCCUPIED for self-service dine-in order {} (previous status: {})",
                diningTable.getTableNumber(), order.getOrderNumber(), previousStatus);
    }

    private void sendOrderNotifications(Order order) {
        try {
            notificationService.notifyNewOrder(order);
        } catch (Exception e) {
            log.error("Failed to create database notifications for order {}: {}", order.getOrderNumber(), e.getMessage());
        }

        try {
            orderEventBroadcaster.broadcastOrderPlaced(order);
        } catch (Exception e) {
            log.error("Failed to broadcast order placed event for order {}: {}", order.getOrderNumber(), e.getMessage());
        }

        // V184 floor map: a QR dine-in order IS the moment a party is seated, and it never passes
        // through updateOrderStatus (it is born NEW), so the map would not learn about it otherwise.
        try {
            if (order.getDiningTable() != null && order.getRestaurant() != null) {
                floorEventBroadcaster.broadcastOccupancyChanged(
                        order.getRestaurant().getId(),
                        order.getDiningTable().getId(),
                        order.getStatus() == null ? null : order.getStatus().name());
            }
        } catch (Exception e) {
            log.warn("Failed to broadcast floor occupancy for order {}: {}",
                    order.getOrderNumber(), e.getMessage());
        }

        try {
            ownerNotificationService.notifyNewOrder(order);
        } catch (Exception e) {
            log.error("Failed to send owner notification for order {}: {}", order.getOrderNumber(), e.getMessage());
        }

        try {
            orderEventPublisher.publishOrderCreated(order, "SELF_SERVICE");
        } catch (Exception e) {
            log.error("Failed to publish order created event for order {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    private SelfServiceOrder createSelfServiceOrder(Order order, SelfServiceSession session,
                                                     SubmitOrderRequest request, SelfServiceSettings settings) {
        SelfServiceOrder ssOrder = SelfServiceOrder.builder()
                .order(order)
                .session(session)
                .qrCode(session.getQrCode())
                .orderType(request.getOrderType())
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .specialInstructions(request.getSpecialInstructions())
                .estimatedReadyTime(LocalDateTime.now().plusMinutes(settings.getEstimatedPrepTimeMinutes()))
                .build();

        return selfServiceOrderRepository.save(ssOrder);
    }

    private void finalizeOrderSubmission(SelfServiceSession session, SubmitOrderRequest request, Customer customer) {
        cartItemRepository.deleteAllBySessionId(session.getId());

        session.setCustomerName(request.getCustomerName());
        session.setCustomerPhone(request.getCustomerPhone());
        session.setCustomer(customer);
        sessionRepository.save(session);
    }

    /**
     * Get order status.
     */
    @Transactional(readOnly = true)
    public SelfServiceOrder getOrderStatus(Long orderId) {
        return selfServiceOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    /**
     * Get settings for a restaurant.
     */
    @Transactional(readOnly = true)
    public SelfServiceSettings getSettings(Long restaurantId) {
        return settingsRepository.findByRestaurantId(restaurantId)
                .orElse(null);
    }

    /**
     * Save settings.
     */
    @Transactional
    public SelfServiceSettings saveSettings(Long restaurantId, SelfServiceSettings settings) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        SelfServiceSettings existing = settingsRepository.findByRestaurantId(restaurantId)
                .orElse(new SelfServiceSettings());

        existing.setRestaurant(restaurant);
        existing.setEnabled(settings.getEnabled());
        existing.setRequirePayment(settings.getRequirePayment());
        existing.setAllowTakeaway(settings.getAllowTakeaway());
        existing.setAllowDineIn(settings.getAllowDineIn());
        existing.setMinimumOrderAmount(settings.getMinimumOrderAmount());
        existing.setServiceChargePercent(settings.getServiceChargePercent());
        existing.setAutoAcceptOrders(settings.getAutoAcceptOrders());
        existing.setEstimatedPrepTimeMinutes(settings.getEstimatedPrepTimeMinutes());
        existing.setShowWaitTime(settings.getShowWaitTime());
        existing.setAllowSpecialInstructions(settings.getAllowSpecialInstructions());
        existing.setMaxItemsPerOrder(settings.getMaxItemsPerOrder());

        return settingsRepository.save(existing);
    }

    /**
     * Get valid session or throw exception.
     */
    private SelfServiceSession getValidSession(String sessionToken) {
        SelfServiceSession session = sessionRepository.findBySessionTokenAndIsActiveTrue(sessionToken)
                .orElseThrow(() -> {
                    log.debug("Session not found or inactive: token={}", sessionToken != null ? sessionToken.substring(0, Math.min(8, sessionToken.length())) + "..." : "null");
                    return new SessionNotFoundException();
                });

        // Auto-renew expired sessions so orders are never blocked by expiry
        if (!session.isValid()) {
            log.info("Session expired — auto-renewing: id={}, was expiresAt={}", session.getId(), session.getExpiresAt());
            session.setExpiresAt(LocalDateTime.now().plusHours(SESSION_EXPIRY_HOURS));
            session = sessionRepository.save(session);
        }

        return session;
    }

    /**
     * Convert cart item to response DTO.
     */
    private CartItemResponse toCartItemResponse(SelfServiceCartItem item) {
        Product product = item.getProduct();
        boolean isBundle = Boolean.TRUE.equals(item.getIsBundle());

        List<CartItemResponse.ModifierResponse> modifiers = item.getModifiers().stream()
                .map(m -> CartItemResponse.ModifierResponse.builder()
                        .id(m.getId())
                        .linkedItemId(m.getLinkedItem().getId())
                        .name(m.getLinkedItem().getLinkedProduct().getName())
                        .quantity(m.getQuantity())
                        .price(m.getPrice())
                        .build())
                .toList();

        return CartItemResponse.builder()
                .id(item.getId())
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getName() : null)
                .imageUrl(product != null ? product.getImageUrl() : null)
                .bundleId(item.getBundleId())
                .bundleName(item.getBundleName())
                .isBundle(isBundle)
                .variantId(item.getVariant() != null ? item.getVariant().getId() : null)
                .variantName(item.getVariant() != null ? item.getVariant().getName() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .specialInstructions(item.getSpecialInstructions())
                .modifiers(modifiers)
                .build();
    }

    // ==================== SECURITY METHODS ====================

    /**
     * Get order status with session validation to prevent IDOR attacks.
     * Only the session that created the order can access its status.
     */
    @Transactional(readOnly = true)
    public SelfServiceOrder getOrderStatusWithSessionValidation(String sessionToken, Long orderId) {
        SelfServiceSession session = sessionRepository.findBySessionTokenAndIsActiveTrue(sessionToken)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found or expired"));

        SelfServiceOrder order = selfServiceOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Validate that the order belongs to this session
        if (order.getSession() == null || !order.getSession().getId().equals(session.getId())) {
            log.warn("IDOR attempt: Session {} tried to access order {} belonging to session {}",
                    session.getId(), orderId, order.getSession() != null ? order.getSession().getId() : "null");
            throw new ResourceNotFoundException("Order not found"); // Don't reveal that order exists
        }

        return order;
    }

    /**
     * Validates phone number format.
     * Allows international formats with optional + prefix.
     */
    private boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }

        // Remove common formatting characters for digit count check
        String digitsOnly = phone.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 7 || digitsOnly.length() > 15) {
            return false;
        }

        return PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * Sanitizes text input to prevent XSS and limits length.
     * Uses HTML escaping to neutralize any script injection attempts.
     */
    private String sanitizeTextInput(String input, int maxLength) {
        if (input == null) {
            return null;
        }

        // Trim and limit length
        String sanitized = input.trim();
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }

        // HTML escape to prevent XSS
        sanitized = HtmlUtils.htmlEscape(sanitized);

        return sanitized.isBlank() ? null : sanitized;
    }
}
