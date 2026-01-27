package com.elcafe.modules.selfservice.service;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.enums.RegistrationSource;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.menu.entity.LinkedItem;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.LinkedItemRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
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
import com.elcafe.modules.selfservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    @Lazy private final OrderEventPublisher orderEventPublisher;
    @Lazy private final NotificationService notificationService;

    private static final int SESSION_EXPIRY_HOURS = 4;

    /**
     * Start a new self-service session by scanning QR code.
     */
    @Transactional
    public SelfServiceSession startSession(String qrCode, String deviceInfo, String ipAddress) {
        QRCode qr = qrCodeRepository.findByCode(qrCode)
                .orElseThrow(() -> new RuntimeException("Invalid QR code"));

        if (!qr.isValid()) {
            throw new RuntimeException("QR code is expired or inactive");
        }

        // Check if self-service is enabled
        SelfServiceSettings settings = settingsRepository.findByRestaurantId(qr.getRestaurant().getId())
                .orElse(null);
        if (settings == null || !settings.getEnabled()) {
            throw new RuntimeException("Self-service ordering is not enabled for this restaurant");
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
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        // Check if self-service is enabled and takeaway is allowed
        SelfServiceSettings settings = settingsRepository.findByRestaurantId(restaurantId)
                .orElse(null);
        if (settings == null || !settings.getEnabled()) {
            throw new RuntimeException("Self-service ordering is not enabled for this restaurant");
        }
        if (!Boolean.TRUE.equals(settings.getAllowTakeaway())) {
            throw new RuntimeException("Takeaway orders are not enabled for this restaurant");
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
    public Optional<SelfServiceSession> getSession(String sessionToken) {
        return sessionRepository.findBySessionTokenAndIsActiveTrue(sessionToken);
    }

    /**
     * Add item to cart.
     */
    @Transactional
    public SelfServiceCartItem addToCart(String sessionToken, AddToCartRequest request) {
        SelfServiceSession session = getValidSession(sessionToken);

        // Handle bundle
        if (Boolean.TRUE.equals(request.getIsBundle()) && request.getBundleId() != null) {
            return addBundleToCart(session, request);
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Check if product belongs to session's restaurant
        if (!product.getCategory().getRestaurant().getId().equals(session.getRestaurant().getId())) {
            throw new RuntimeException("Product not available at this restaurant");
        }

        ProductVariant variant = null;
        BigDecimal unitPrice = product.getPrice();

        if (request.getVariantId() != null) {
            variant = variantRepository.findById(request.getVariantId())
                    .orElseThrow(() -> new RuntimeException("Variant not found"));
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

        // Add modifiers
        if (request.getModifiers() != null && !request.getModifiers().isEmpty()) {
            for (AddToCartRequest.ModifierRequest modReq : request.getModifiers()) {
                LinkedItem linkedItem = linkedItemRepository.findById(modReq.getLinkedItemId())
                        .orElseThrow(() -> new RuntimeException("Modifier not found"));

                if (linkedItem.getLinkedProduct() == null) {
                    throw new RuntimeException("Modifier product not found for linked item: " + linkedItem.getId());
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
        var bundle = bundleRepository.findById(request.getBundleId())
                .orElseThrow(() -> new RuntimeException("Bundle not found"));

        // Check if bundle belongs to session's restaurant
        if (!bundle.getRestaurant().getId().equals(session.getRestaurant().getId())) {
            throw new RuntimeException("Bundle not available at this restaurant");
        }

        // Check if bundle is active and currently available
        if (!bundle.isCurrentlyAvailable()) {
            throw new RuntimeException("Bundle is not currently available");
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
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!item.getSession().getId().equals(session.getId())) {
            throw new RuntimeException("Cart item does not belong to this session");
        }

        if (quantity <= 0) {
            cartItemRepository.delete(item);
            return null;
        }

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

        SelfServiceCartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!item.getSession().getId().equals(session.getId())) {
            throw new RuntimeException("Cart item does not belong to this session");
        }

        cartItemRepository.delete(item);
        session.touch();
        sessionRepository.save(session);
    }

    /**
     * Get cart contents.
     */
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

        // Validate customer details for takeaway orders
        if (request.getOrderType() == SelfServiceOrderType.TAKEAWAY) {
            if (request.getCustomerName() == null || request.getCustomerName().trim().isEmpty()) {
                throw new RuntimeException("Customer name is required for takeaway orders");
            }
            if (request.getCustomerPhone() == null || request.getCustomerPhone().trim().isEmpty()) {
                throw new RuntimeException("Customer phone is required for takeaway orders");
            }
        }

        List<SelfServiceCartItem> cartItems = cartItemRepository.findBySessionIdOrderByAddedAtAsc(session.getId());
        if (cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        SelfServiceSettings settings = settingsRepository.findByRestaurantId(session.getRestaurant().getId())
                .orElseThrow(() -> new RuntimeException("Self-service not configured"));

        // Calculate totals
        BigDecimal subtotal = cartItems.stream()
                .map(SelfServiceCartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Check minimum order amount
        if (subtotal.compareTo(settings.getMinimumOrderAmount()) < 0) {
            throw new RuntimeException("Minimum order amount not met");
        }

        // Find or create customer by phone (phone is the unique key for customers)
        Customer customer = null;
        if (request.getCustomerPhone() != null && !request.getCustomerPhone().isEmpty()) {
            String phone = request.getCustomerPhone().trim();
            String customerName = request.getCustomerName() != null ? request.getCustomerName().trim() : "";

            customer = customerRepository.findByPhone(phone)
                    .map(existingCustomer -> {
                        // Update name if provided and customer has no name set
                        if (!customerName.isEmpty() &&
                            (existingCustomer.getFirstName() == null || existingCustomer.getFirstName().isEmpty()
                             || "Customer".equals(existingCustomer.getFirstName()))) {
                            existingCustomer.setFirstName(customerName);
                            return customerRepository.save(existingCustomer);
                        }
                        return existingCustomer;
                    })
                    .orElseGet(() -> {
                        // Create new customer with phone as unique key
                        Customer newCustomer = Customer.builder()
                                .phone(phone)
                                .firstName(customerName.isEmpty() ? "Customer" : customerName)
                                .lastName("")
                                .registrationSource(RegistrationSource.QR_ORDER)
                                .active(true)
                                .build();
                        log.info("Creating new customer from self-service order: phone={}", phone);
                        return customerRepository.save(newCustomer);
                    });
        }

        // Get customer notes (frontend sends "notes", DTO also supports "specialInstructions")
        String customerNotes = request.getNotes() != null ? request.getNotes() : request.getSpecialInstructions();

        // Map SelfServiceOrderType to OrderType
        OrderType orderType = request.getOrderType() == SelfServiceOrderType.TAKEAWAY
                ? OrderType.TAKEAWAY
                : OrderType.DINE_IN;

        // Fetch fresh table reference for dine-in orders to ensure proper linking
        RestaurantTable diningTable = null;
        if (orderType == OrderType.DINE_IN && session.getTable() != null) {
            diningTable = restaurantTableRepository.findById(session.getTable().getId()).orElse(null);
            log.info("Setting dining table for self-service dine-in order: tableId={}, tableNumber={}",
                    diningTable != null ? diningTable.getId() : null,
                    diningTable != null ? diningTable.getTableNumber() : null);
        }

        // Create main order
        Order order = Order.builder()
                .orderNumber(dailyOrderSequenceService.generateNextOrderNumber())
                .restaurant(session.getRestaurant())
                .customer(customer)
                .diningTable(diningTable)
                .orderType(orderType)
                .status(settings.getAutoAcceptOrders() ? OrderStatus.ACCEPTED : OrderStatus.PENDING)
                .subtotal(subtotal)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .total(subtotal)
                .customerNotes(customerNotes)
                .placedAt(LocalDateTime.now())
                .build();

        // Convert cart items to order items
        List<OrderItem> orderItems = new ArrayList<>();
        for (SelfServiceCartItem cartItem : cartItems) {
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .quantity(cartItem.getQuantity())
                    .unitPrice(cartItem.getUnitPrice())
                    .totalPrice(cartItem.getTotalPrice())
                    .specialInstructions(cartItem.getSpecialInstructions())
                    .build();

            // Handle bundle vs regular product
            if (Boolean.TRUE.equals(cartItem.getIsBundle()) && cartItem.getBundleId() != null) {
                // Bundle item - set bundle fields for analytics tracking
                orderItem.setProductId(cartItem.getBundleId()); // Use bundleId as productId for reference
                orderItem.setProductName(cartItem.getBundleName());
                orderItem.setBundleId(cartItem.getBundleId());
                orderItem.setBundleName(cartItem.getBundleName());
                orderItem.setIsBundle(true);
            } else {
                // Regular product
                orderItem.setProductId(cartItem.getProduct().getId());
                orderItem.setProductName(cartItem.getProduct().getName());
                orderItem.setVariantId(cartItem.getVariant() != null ? cartItem.getVariant().getId() : null);
                orderItem.setVariantName(cartItem.getVariant() != null ? cartItem.getVariant().getName() : null);
                orderItem.setIsBundle(false);
            }

            orderItems.add(orderItem);
        }
        order.setItems(orderItems);

        // Apply coupon if provided
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            try {
                // Build validation request from order
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

                if (couponResponse.getValid()) {
                    // Apply the discount using DiscountCalculationService
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
                // Continue without discount - don't fail the order
            }
        }

        // Recalculate total after discount
        BigDecimal discount = order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO;
        order.setTotal(subtotal.subtract(discount));

        order = orderRepository.save(order);

        // Update table status to OCCUPIED for dine-in orders
        // The diningTable variable was already fetched fresh above when creating the order
        if (orderType == OrderType.DINE_IN && diningTable != null) {
            // Update table status regardless of current status (AVAILABLE, RESERVED, etc.)
            // This ensures the table is marked as OCCUPIED when an order is placed
            RestaurantTable.TableStatus previousStatus = diningTable.getStatus();
            diningTable.setStatus(RestaurantTable.TableStatus.OCCUPIED);
            restaurantTableRepository.save(diningTable);
            log.info("Table {} marked as OCCUPIED for self-service dine-in order {} (previous status: {})",
                    diningTable.getTableNumber(), order.getOrderNumber(), previousStatus);
        }

        // Send database notifications (for admin panel, kitchen, restaurant, customer)
        try {
            notificationService.notifyNewOrder(order);
        } catch (Exception e) {
            log.error("Failed to create database notifications for order {}: {}", order.getOrderNumber(), e.getMessage());
        }

        // Send notifications to admin panel via WebSocket
        try {
            orderEventBroadcaster.broadcastOrderPlaced(order);
        } catch (Exception e) {
            log.error("Failed to broadcast order placed event for order {}: {}", order.getOrderNumber(), e.getMessage());
        }

        // Send notification to owner via Telegram
        try {
            ownerNotificationService.notifyNewOrder(order);
        } catch (Exception e) {
            log.error("Failed to send owner notification for order {}: {}", order.getOrderNumber(), e.getMessage());
        }

        // Send notification to waiters via WebSocket event system
        try {
            orderEventPublisher.publishOrderCreated(order, "SELF_SERVICE");
        } catch (Exception e) {
            log.error("Failed to publish order created event for order {}: {}", order.getOrderNumber(), e.getMessage());
        }

        // Create self-service order metadata
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

        ssOrder = selfServiceOrderRepository.save(ssOrder);

        // Clear cart
        cartItemRepository.deleteAllBySessionId(session.getId());

        // Update session with customer info
        session.setCustomerName(request.getCustomerName());
        session.setCustomerPhone(request.getCustomerPhone());
        session.setCustomer(customer);
        sessionRepository.save(session);

        log.info("Self-service order created: {} for session {}", order.getId(), session.getId());

        return ssOrder;
    }

    /**
     * Get order status.
     */
    public SelfServiceOrder getOrderStatus(Long orderId) {
        return selfServiceOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    /**
     * Get settings for a restaurant.
     */
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
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

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
                .orElseThrow(() -> new RuntimeException("Session not found or expired"));

        if (!session.isValid()) {
            throw new RuntimeException("Session expired");
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
}
