package com.elcafe.modules.selfservice.service;

import com.elcafe.modules.bundle.repository.BundleRepository;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.LinkedItem;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.LinkedItemRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.DailyOrderSequenceService;
import com.elcafe.modules.order.service.OrderEventBroadcaster;
import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.promotion.service.CouponValidationService;
import com.elcafe.modules.promotion.service.DiscountCalculationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import com.elcafe.modules.selfservice.dto.AddToCartRequest;
import com.elcafe.modules.selfservice.entity.QRCode;
import com.elcafe.modules.selfservice.entity.SelfServiceCartItem;
import com.elcafe.modules.selfservice.entity.SelfServiceCartModifier;
import com.elcafe.modules.selfservice.entity.SelfServiceSession;
import com.elcafe.modules.selfservice.entity.SelfServiceSettings;
import com.elcafe.modules.selfservice.exception.CartOperationException;
import com.elcafe.modules.selfservice.exception.ProductNotFoundException;
import com.elcafe.modules.selfservice.exception.SelfServiceException;
import com.elcafe.modules.selfservice.exception.SessionNotFoundException;
import com.elcafe.modules.selfservice.repository.QRCodeRepository;
import com.elcafe.modules.selfservice.repository.SelfServiceCartItemRepository;
import com.elcafe.modules.selfservice.repository.SelfServiceOrderRepository;
import com.elcafe.modules.selfservice.repository.SelfServiceSessionRepository;
import com.elcafe.modules.selfservice.repository.SelfServiceSettingsRepository;
import com.elcafe.modules.waiter.event.OrderEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.selfservice.dto.CartItemResponse;
import com.elcafe.modules.selfservice.dto.SubmitOrderRequest;
import com.elcafe.modules.selfservice.entity.SelfServiceOrder;
import com.elcafe.modules.selfservice.enums.SelfServiceOrderType;
import com.elcafe.modules.selfservice.exception.OrderSubmissionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SelfServiceOrderServiceTest {

    @Mock
    private SelfServiceSessionRepository sessionRepository;

    @Mock
    private SelfServiceCartItemRepository cartItemRepository;

    @Mock
    private SelfServiceOrderRepository selfServiceOrderRepository;

    @Mock
    private SelfServiceSettingsRepository settingsRepository;

    @Mock
    private QRCodeRepository qrCodeRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private LinkedItemRepository linkedItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantTableRepository restaurantTableRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private DailyOrderSequenceService dailyOrderSequenceService;

    @Mock
    private BundleRepository bundleRepository;

    @Mock
    private CouponValidationService couponValidationService;

    @Mock
    private DiscountCalculationService discountCalculationService;

    @Mock
    private OwnerNotificationService ownerNotificationService;

    @Mock
    private OrderEventBroadcaster orderEventBroadcaster;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private NotificationService notificationService;

    @Mock
    private com.elcafe.modules.menu.service.PackagingService packagingService;

    @Mock
    private com.elcafe.modules.kitchen.service.KitchenOrderService kitchenOrderService;

    @InjectMocks
    private SelfServiceOrderService service;

    // ========== Helper Methods ==========

    private Restaurant createRestaurant(Long id) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(id);
        restaurant.setName("Test Restaurant");
        return restaurant;
    }

    private RestaurantTable createTable(Long id) {
        RestaurantTable table = new RestaurantTable();
        table.setId(id);
        table.setTableNumber("T1");
        return table;
    }

    private QRCode createValidQRCode(Restaurant restaurant, RestaurantTable table) {
        return QRCode.builder()
                .id(1L)
                .code("VALID-QR-CODE")
                .restaurant(restaurant)
                .table(table)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }

    private QRCode createExpiredQRCode(Restaurant restaurant, RestaurantTable table) {
        return QRCode.builder()
                .id(2L)
                .code("EXPIRED-QR-CODE")
                .restaurant(restaurant)
                .table(table)
                .isActive(true)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    private SelfServiceSettings createEnabledSettings(Restaurant restaurant) {
        return SelfServiceSettings.builder()
                .id(1L)
                .restaurant(restaurant)
                .enabled(true)
                .allowTakeaway(true)
                .build();
    }

    private SelfServiceSettings createDisabledSettings(Restaurant restaurant) {
        return SelfServiceSettings.builder()
                .id(1L)
                .restaurant(restaurant)
                .enabled(false)
                .build();
    }

    private SelfServiceSession createValidSession(Long id, Restaurant restaurant, RestaurantTable table) {
        return SelfServiceSession.builder()
                .id(id)
                .sessionToken("test-session-token")
                .restaurant(restaurant)
                .table(table)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .cartItems(new ArrayList<>())
                .build();
    }

    private Product createProduct(Long id, Restaurant restaurant) {
        Category category = new Category();
        category.setId(1L);
        category.setRestaurant(restaurant);

        Product product = new Product();
        product.setId(id);
        product.setName("Test Product");
        product.setPrice(BigDecimal.valueOf(10.00));
        product.setCategory(category);
        return product;
    }

    private ProductVariant createVariant(Long id, Product product, BigDecimal price) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setProduct(product);
        variant.setName("Large");
        variant.setPrice(price);
        return variant;
    }

    private AddToCartRequest createAddToCartRequest(Long productId, int quantity) {
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);
        return request;
    }

    // ========== Session Management Tests ==========

    @Test
    void startSession_validQRCode_createsSession() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        QRCode qrCode = createValidQRCode(restaurant, table);
        SelfServiceSettings settings = createEnabledSettings(restaurant);

        when(qrCodeRepository.findByCode("VALID-QR-CODE")).thenReturn(Optional.of(qrCode));
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));
        when(qrCodeRepository.save(any(QRCode.class))).thenReturn(qrCode);
        when(sessionRepository.save(any(SelfServiceSession.class)))
                .thenAnswer(invocation -> {
                    SelfServiceSession s = invocation.getArgument(0);
                    s.setId(1L);
                    return s;
                });

        SelfServiceSession session = service.startSession("VALID-QR-CODE", "TestDevice", "127.0.0.1");

        assertNotNull(session);
        assertNotNull(session.getSessionToken());
        assertEquals(restaurant, session.getRestaurant());
        assertEquals(table, session.getTable());
        verify(sessionRepository).save(any(SelfServiceSession.class));
    }

    @Test
    void startSession_invalidQRCode_throwsSelfServiceException() {
        when(qrCodeRepository.findByCode("INVALID-CODE")).thenReturn(Optional.empty());

        assertThrows(SelfServiceException.class,
                () -> service.startSession("INVALID-CODE", "TestDevice", "127.0.0.1"));
    }

    @Test
    void startSession_expiredQRCode_throwsSelfServiceException() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        QRCode expiredQr = createExpiredQRCode(restaurant, table);

        when(qrCodeRepository.findByCode("EXPIRED-QR-CODE")).thenReturn(Optional.of(expiredQr));

        assertThrows(SelfServiceException.class,
                () -> service.startSession("EXPIRED-QR-CODE", "TestDevice", "127.0.0.1"));
    }

    @Test
    void startSession_selfServiceDisabled_throwsException() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        QRCode qrCode = createValidQRCode(restaurant, table);
        SelfServiceSettings disabledSettings = createDisabledSettings(restaurant);

        when(qrCodeRepository.findByCode("VALID-QR-CODE")).thenReturn(Optional.of(qrCode));
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(disabledSettings));

        assertThrows(RuntimeException.class,
                () -> service.startSession("VALID-QR-CODE", "TestDevice", "127.0.0.1"));
    }

    @Test
    void startTakeawaySession_valid_createsSession() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSettings settings = createEnabledSettings(restaurant);

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));
        when(sessionRepository.save(any(SelfServiceSession.class)))
                .thenAnswer(invocation -> {
                    SelfServiceSession s = invocation.getArgument(0);
                    s.setId(1L);
                    return s;
                });

        SelfServiceSession session = service.startTakeawaySession(1L, "TestDevice", "127.0.0.1");

        assertNotNull(session);
        assertNotNull(session.getSessionToken());
        assertEquals(restaurant, session.getRestaurant());
        assertNull(session.getTable());
        verify(sessionRepository).save(any(SelfServiceSession.class));
    }

    @Test
    void startTakeawaySession_takeawayNotAllowed_throwsException() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSettings settings = SelfServiceSettings.builder()
                .id(1L)
                .restaurant(restaurant)
                .enabled(true)
                .allowTakeaway(false)
                .build();

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        assertThrows(RuntimeException.class,
                () -> service.startTakeawaySession(1L, "TestDevice", "127.0.0.1"));
    }

    @Test
    void getSession_validToken_returnsSession() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));

        Optional<SelfServiceSession> result = service.getSession("test-session-token");

        assertTrue(result.isPresent());
        assertEquals(session, result.get());
    }

    // ========== Cart Operations Tests ==========

    @Test
    void addToCart_newProduct_createsCartItem() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);
        Product product = createProduct(10L, restaurant);
        AddToCartRequest request = createAddToCartRequest(10L, 2);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findBySessionIdAndProductIdAndVariantId(1L, 10L, null))
                .thenReturn(Optional.empty());
        when(cartItemRepository.save(any(SelfServiceCartItem.class)))
                .thenAnswer(invocation -> {
                    SelfServiceCartItem item = invocation.getArgument(0);
                    item.setId(1L);
                    return item;
                });
        when(sessionRepository.save(any(SelfServiceSession.class))).thenReturn(session);

        SelfServiceCartItem result = service.addToCart("test-session-token", request);

        assertNotNull(result);
        assertEquals(2, result.getQuantity());
        assertEquals(product, result.getProduct());
        assertEquals(BigDecimal.valueOf(10.00), result.getUnitPrice());
        verify(cartItemRepository).save(any(SelfServiceCartItem.class));
    }

    @Test
    void addToCart_existingProduct_mergesQuantity() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);
        Product product = createProduct(10L, restaurant);

        SelfServiceCartItem existingItem = SelfServiceCartItem.builder()
                .id(5L)
                .session(session)
                .product(product)
                .quantity(3)
                .unitPrice(BigDecimal.valueOf(10.00))
                .modifiers(new ArrayList<>())
                .build();

        AddToCartRequest request = createAddToCartRequest(10L, 2);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findBySessionIdAndProductIdAndVariantId(1L, 10L, null))
                .thenReturn(Optional.of(existingItem));
        when(cartItemRepository.save(any(SelfServiceCartItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SelfServiceCartItem result = service.addToCart("test-session-token", request);

        assertNotNull(result);
        assertEquals(5, result.getQuantity());
        verify(cartItemRepository).save(existingItem);
    }

    @Test
    void addToCart_withVariant_usesVariantPrice() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);
        Product product = createProduct(10L, restaurant);
        ProductVariant variant = createVariant(20L, product, BigDecimal.valueOf(15.00));

        AddToCartRequest request = createAddToCartRequest(10L, 1);
        request.setVariantId(20L);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(variantRepository.findById(20L)).thenReturn(Optional.of(variant));
        when(cartItemRepository.findBySessionIdAndProductIdAndVariantId(1L, 10L, 20L))
                .thenReturn(Optional.empty());
        when(cartItemRepository.save(any(SelfServiceCartItem.class)))
                .thenAnswer(invocation -> {
                    SelfServiceCartItem item = invocation.getArgument(0);
                    item.setId(1L);
                    return item;
                });
        when(sessionRepository.save(any(SelfServiceSession.class))).thenReturn(session);

        SelfServiceCartItem result = service.addToCart("test-session-token", request);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(15.00), result.getUnitPrice());
        assertEquals(variant, result.getVariant());
    }

    @Test
    void addToCart_withModifiers_addsModifiers() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);
        Product product = createProduct(10L, restaurant);

        Product modifierProduct = new Product();
        modifierProduct.setId(50L);
        modifierProduct.setName("Extra Cheese");
        modifierProduct.setPrice(BigDecimal.valueOf(2.00));

        LinkedItem linkedItem = new LinkedItem();
        linkedItem.setId(30L);
        linkedItem.setLinkedProduct(modifierProduct);

        AddToCartRequest request = createAddToCartRequest(10L, 1);
        AddToCartRequest.ModifierRequest modReq = new AddToCartRequest.ModifierRequest();
        modReq.setLinkedItemId(30L);
        modReq.setQuantity(1);
        request.setModifiers(List.of(modReq));

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findBySessionIdAndProductIdAndVariantId(1L, 10L, null))
                .thenReturn(Optional.empty());
        when(linkedItemRepository.findAllByIdWithLinkedProduct(List.of(30L)))
                .thenReturn(List.of(linkedItem));
        when(cartItemRepository.save(any(SelfServiceCartItem.class)))
                .thenAnswer(invocation -> {
                    SelfServiceCartItem item = invocation.getArgument(0);
                    if (item.getId() == null) {
                        item.setId(1L);
                    }
                    return item;
                });
        when(sessionRepository.save(any(SelfServiceSession.class))).thenReturn(session);

        SelfServiceCartItem result = service.addToCart("test-session-token", request);

        assertNotNull(result);
        assertFalse(result.getModifiers().isEmpty());
        assertEquals(1, result.getModifiers().size());
        SelfServiceCartModifier modifier = result.getModifiers().get(0);
        assertEquals(linkedItem, modifier.getLinkedItem());
        assertEquals(BigDecimal.valueOf(2.00), modifier.getPrice());
    }

    @Test
    void addToCart_bundle_createsBundleCartItem() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);

        com.elcafe.modules.bundle.entity.Bundle bundle = new com.elcafe.modules.bundle.entity.Bundle();
        bundle.setId(100L);
        bundle.setName("Combo Meal");
        bundle.setBundlePrice(BigDecimal.valueOf(25.00));
        bundle.setRestaurant(restaurant);
        bundle.setActive(true);

        AddToCartRequest request = new AddToCartRequest();
        request.setBundleId(100L);
        request.setIsBundle(true);
        request.setQuantity(1);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(bundleRepository.findById(100L)).thenReturn(Optional.of(bundle));
        when(cartItemRepository.findBySessionIdAndBundleId(1L, 100L))
                .thenReturn(Optional.empty());
        when(cartItemRepository.save(any(SelfServiceCartItem.class)))
                .thenAnswer(invocation -> {
                    SelfServiceCartItem item = invocation.getArgument(0);
                    item.setId(1L);
                    return item;
                });
        when(sessionRepository.save(any(SelfServiceSession.class))).thenReturn(session);

        SelfServiceCartItem result = service.addToCart("test-session-token", request);

        assertNotNull(result);
        assertTrue(result.getIsBundle());
        assertEquals(100L, result.getBundleId());
        assertEquals("Combo Meal", result.getBundleName());
        assertEquals(BigDecimal.valueOf(25.00), result.getUnitPrice());
    }

    @Test
    void addToCart_productNotFound_throwsProductNotFoundException() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);
        AddToCartRequest request = createAddToCartRequest(999L, 1);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> service.addToCart("test-session-token", request));
    }

    @Test
    void addToCart_productNotAtRestaurant_throwsCartOperationException() {
        Restaurant restaurant = createRestaurant(1L);
        Restaurant otherRestaurant = createRestaurant(2L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);
        Product product = createProduct(10L, otherRestaurant);
        AddToCartRequest request = createAddToCartRequest(10L, 1);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThrows(CartOperationException.class,
                () -> service.addToCart("test-session-token", request));
    }

    @Test
    void updateCartItem_validQuantity_updatesItem() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);

        SelfServiceCartItem item = SelfServiceCartItem.builder()
                .id(5L)
                .session(session)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(10.00))
                .modifiers(new ArrayList<>())
                .build();

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(cartItemRepository.save(any(SelfServiceCartItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(SelfServiceSession.class))).thenReturn(session);

        SelfServiceCartItem result = service.updateCartItem("test-session-token", 5L, 7);

        assertNotNull(result);
        assertEquals(7, result.getQuantity());
        verify(cartItemRepository).save(item);
    }

    @Test
    void updateCartItem_zeroQuantity_removesItem() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);

        SelfServiceCartItem item = SelfServiceCartItem.builder()
                .id(5L)
                .session(session)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(10.00))
                .modifiers(new ArrayList<>())
                .build();

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        SelfServiceCartItem result = service.updateCartItem("test-session-token", 5L, 0);

        assertNull(result);
        verify(cartItemRepository).delete(item);
    }

    @Test
    void updateCartItem_wrongSession_throwsCartOperationException() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);

        SelfServiceSession otherSession = createValidSession(2L, restaurant, table);
        otherSession.setSessionToken("other-session-token");

        SelfServiceCartItem item = SelfServiceCartItem.builder()
                .id(5L)
                .session(otherSession)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(10.00))
                .modifiers(new ArrayList<>())
                .build();

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        assertThrows(CartOperationException.class,
                () -> service.updateCartItem("test-session-token", 5L, 3));
    }

    // ==================== Tests 18-21: Cart (remaining) ====================

    @Test
    void removeFromCart_validItem_deletesItem() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);

        SelfServiceCartItem item = SelfServiceCartItem.builder()
                .id(5L).session(session).quantity(2).unitPrice(BigDecimal.TEN)
                .modifiers(new ArrayList<>()).build();

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        service.removeFromCart("test-session-token", 5L);

        verify(cartItemRepository).delete(item);
    }

    @Test
    void removeFromCart_notFound_throwsCartOperationException() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, createTable(1L));

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CartOperationException.class,
                () -> service.removeFromCart("test-session-token", 99L));
    }

    @Test
    void getCart_returnsCartItemResponses() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, createTable(1L));
        Product product = createProduct(1L, restaurant);

        SelfServiceCartItem cartItem = SelfServiceCartItem.builder()
                .id(1L).session(session).product(product).quantity(2)
                .unitPrice(BigDecimal.TEN).isBundle(false).modifiers(new ArrayList<>()).build();

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findBySessionIdOrderByAddedAtAsc(session.getId()))
                .thenReturn(List.of(cartItem));

        List<CartItemResponse> result = service.getCart("test-session-token");

        assertEquals(1, result.size());
        assertEquals(product.getId(), result.get(0).getProductId());
        assertEquals(2, result.get(0).getQuantity());
    }

    @Test
    void clearCart_deletesAllItems() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, createTable(1L));

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));

        service.clearCart("test-session-token");

        verify(cartItemRepository).deleteAllBySessionId(session.getId());
    }

    // ==================== Tests 22-29: Order Submission ====================

    @Test
    void submitOrder_validDineIn_createsOrder() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);

        SelfServiceSettings settings = createEnabledSettings(restaurant);
        settings.setMinimumOrderAmount(BigDecimal.ZERO);
        settings.setEstimatedPrepTimeMinutes(15);
        settings.setAutoAcceptOrders(false);

        Product product = createProduct(1L, restaurant);
        SelfServiceCartItem cartItem = SelfServiceCartItem.builder()
                .id(1L).session(session).product(product).quantity(2)
                .unitPrice(BigDecimal.TEN).isBundle(false).modifiers(new ArrayList<>()).build();

        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setOrderType(SelfServiceOrderType.DINE_IN);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findBySessionIdOrderByAddedAtAsc(session.getId()))
                .thenReturn(List.of(cartItem));
        when(settingsRepository.findByRestaurantId(restaurant.getId()))
                .thenReturn(Optional.of(settings));
        when(restaurantTableRepository.findById(table.getId()))
                .thenReturn(Optional.of(table));
        when(dailyOrderSequenceService.generateNextOrderNumber()).thenReturn("ORD-001");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0); o.setId(1L); return o;
        });
        when(selfServiceOrderRepository.save(any(SelfServiceOrder.class))).thenAnswer(inv -> {
            SelfServiceOrder o = inv.getArgument(0); o.setId(1L); return o;
        });

        SelfServiceOrder result = service.submitOrder("test-session-token", request);

        assertNotNull(result);
        verify(orderRepository).save(any(Order.class));
        verify(selfServiceOrderRepository).save(any(SelfServiceOrder.class));
        verify(restaurantTableRepository).save(table); // table set to OCCUPIED
        // Not auto-accepted (born PENDING) → the kitchen ticket waits for an operator to accept.
        verify(kitchenOrderService, never()).createKitchenOrderIfAbsent(any(Order.class));
    }

    @Test
    void submitOrder_autoAccept_putsOrderOnKitchenBoard() {
        Restaurant restaurant = createRestaurant(1L);
        RestaurantTable table = createTable(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, table);

        SelfServiceSettings settings = createEnabledSettings(restaurant);
        settings.setMinimumOrderAmount(BigDecimal.ZERO);
        settings.setEstimatedPrepTimeMinutes(15);
        settings.setAutoAcceptOrders(true); // born ACCEPTED → straight to the kitchen display

        Product product = createProduct(1L, restaurant);
        SelfServiceCartItem cartItem = SelfServiceCartItem.builder()
                .id(1L).session(session).product(product).quantity(2)
                .unitPrice(BigDecimal.TEN).isBundle(false).modifiers(new ArrayList<>()).build();

        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setOrderType(SelfServiceOrderType.DINE_IN);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findBySessionIdOrderByAddedAtAsc(session.getId()))
                .thenReturn(List.of(cartItem));
        when(settingsRepository.findByRestaurantId(restaurant.getId()))
                .thenReturn(Optional.of(settings));
        when(restaurantTableRepository.findById(table.getId()))
                .thenReturn(Optional.of(table));
        when(dailyOrderSequenceService.generateNextOrderNumber()).thenReturn("ORD-001");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0); o.setId(1L); return o;
        });
        when(selfServiceOrderRepository.save(any(SelfServiceOrder.class))).thenAnswer(inv -> {
            SelfServiceOrder o = inv.getArgument(0); o.setId(1L); return o;
        });

        service.submitOrder("test-session-token", request);

        verify(kitchenOrderService).createKitchenOrderIfAbsent(any(Order.class));
    }

    @Test
    void submitOrder_validTakeaway_requiresCustomerDetails() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, null);

        SelfServiceSettings settings = createEnabledSettings(restaurant);
        settings.setMinimumOrderAmount(BigDecimal.ZERO);
        settings.setEstimatedPrepTimeMinutes(15);

        Product product = createProduct(1L, restaurant);
        SelfServiceCartItem cartItem = SelfServiceCartItem.builder()
                .id(1L).session(session).product(product).quantity(1)
                .unitPrice(BigDecimal.TEN).isBundle(false).modifiers(new ArrayList<>()).build();

        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setOrderType(SelfServiceOrderType.TAKEAWAY);
        request.setCustomerName("John");
        request.setCustomerPhone("+998901234567");

        Customer customer = Customer.builder().id(1L).firstName("John").phone("+998901234567").build();

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findBySessionIdOrderByAddedAtAsc(session.getId()))
                .thenReturn(List.of(cartItem));
        when(settingsRepository.findByRestaurantId(restaurant.getId()))
                .thenReturn(Optional.of(settings));
        when(customerRepository.findByPhoneAndRestaurantId("+998901234567", restaurant.getId())).thenReturn(Optional.of(customer));
        when(dailyOrderSequenceService.generateNextOrderNumber()).thenReturn("ORD-002");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0); o.setId(2L); return o;
        });
        when(selfServiceOrderRepository.save(any(SelfServiceOrder.class))).thenAnswer(inv -> {
            SelfServiceOrder o = inv.getArgument(0); o.setId(2L); return o;
        });

        SelfServiceOrder result = service.submitOrder("test-session-token", request);

        assertNotNull(result);
        verify(customerRepository).findByPhoneAndRestaurantId("+998901234567", restaurant.getId());
    }

    @Test
    void submitOrder_emptyCart_throwsOrderSubmissionException() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, createTable(1L));

        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setOrderType(SelfServiceOrderType.DINE_IN);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findBySessionIdOrderByAddedAtAsc(session.getId()))
                .thenReturn(Collections.emptyList());

        assertThrows(OrderSubmissionException.class,
                () -> service.submitOrder("test-session-token", request));
    }

    @Test
    void submitOrder_belowMinimum_throwsOrderSubmissionException() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, createTable(1L));

        SelfServiceSettings settings = createEnabledSettings(restaurant);
        settings.setMinimumOrderAmount(BigDecimal.valueOf(50));

        Product product = createProduct(1L, restaurant);
        SelfServiceCartItem cartItem = SelfServiceCartItem.builder()
                .id(1L).session(session).product(product).quantity(1)
                .unitPrice(BigDecimal.TEN).isBundle(false).modifiers(new ArrayList<>()).build();

        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setOrderType(SelfServiceOrderType.DINE_IN);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findBySessionIdOrderByAddedAtAsc(session.getId()))
                .thenReturn(List.of(cartItem));
        when(settingsRepository.findByRestaurantId(restaurant.getId()))
                .thenReturn(Optional.of(settings));

        assertThrows(OrderSubmissionException.class,
                () -> service.submitOrder("test-session-token", request));
    }

    @Test
    void submitOrder_takeawayMissingPhone_throwsOrderSubmissionException() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, null);

        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setOrderType(SelfServiceOrderType.TAKEAWAY);
        request.setCustomerName("John");
        // Missing phone

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));

        assertThrows(OrderSubmissionException.class,
                () -> service.submitOrder("test-session-token", request));
    }

    @Test
    void submitOrder_invalidPhone_throwsOrderSubmissionException() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, null);

        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setOrderType(SelfServiceOrderType.TAKEAWAY);
        request.setCustomerName("John");
        request.setCustomerPhone("abc"); // Invalid phone

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));

        assertThrows(OrderSubmissionException.class,
                () -> service.submitOrder("test-session-token", request));
    }

    @Test
    void submitOrder_withCoupon_appliesDiscount() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, createTable(1L));

        SelfServiceSettings settings = createEnabledSettings(restaurant);
        settings.setMinimumOrderAmount(BigDecimal.ZERO);
        settings.setEstimatedPrepTimeMinutes(15);

        Product product = createProduct(1L, restaurant);
        SelfServiceCartItem cartItem = SelfServiceCartItem.builder()
                .id(1L).session(session).product(product).quantity(2)
                .unitPrice(BigDecimal.TEN).isBundle(false).modifiers(new ArrayList<>()).build();

        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setOrderType(SelfServiceOrderType.DINE_IN);
        request.setCouponCode("SAVE10");

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findBySessionIdOrderByAddedAtAsc(session.getId()))
                .thenReturn(List.of(cartItem));
        when(settingsRepository.findByRestaurantId(restaurant.getId()))
                .thenReturn(Optional.of(settings));
        when(restaurantTableRepository.findById(any())).thenReturn(Optional.of(createTable(1L)));
        when(dailyOrderSequenceService.generateNextOrderNumber()).thenReturn("ORD-003");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0); o.setId(3L); return o;
        });
        when(selfServiceOrderRepository.save(any(SelfServiceOrder.class))).thenAnswer(inv -> {
            SelfServiceOrder o = inv.getArgument(0); o.setId(3L); return o;
        });

        // Mock coupon validation
        com.elcafe.modules.promotion.dto.ValidateCouponResponse couponResponse =
                mock(com.elcafe.modules.promotion.dto.ValidateCouponResponse.class);
        when(couponResponse.getValid()).thenReturn(true);
        when(couponValidationService.validateCoupon(any())).thenReturn(couponResponse);

        SelfServiceOrder result = service.submitOrder("test-session-token", request);

        assertNotNull(result);
        verify(couponValidationService).validateCoupon(any());
        verify(discountCalculationService).applyDiscount(any(Order.class), any());
    }

    @Test
    void submitOrder_sendsNotifications() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSession session = createValidSession(1L, restaurant, createTable(1L));

        SelfServiceSettings settings = createEnabledSettings(restaurant);
        settings.setMinimumOrderAmount(BigDecimal.ZERO);
        settings.setEstimatedPrepTimeMinutes(15);

        Product product = createProduct(1L, restaurant);
        SelfServiceCartItem cartItem = SelfServiceCartItem.builder()
                .id(1L).session(session).product(product).quantity(1)
                .unitPrice(BigDecimal.TEN).isBundle(false).modifiers(new ArrayList<>()).build();

        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setOrderType(SelfServiceOrderType.DINE_IN);

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session));
        when(cartItemRepository.findBySessionIdOrderByAddedAtAsc(session.getId()))
                .thenReturn(List.of(cartItem));
        when(settingsRepository.findByRestaurantId(restaurant.getId()))
                .thenReturn(Optional.of(settings));
        when(restaurantTableRepository.findById(any())).thenReturn(Optional.of(createTable(1L)));
        when(dailyOrderSequenceService.generateNextOrderNumber()).thenReturn("ORD-004");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0); o.setId(4L); return o;
        });
        when(selfServiceOrderRepository.save(any(SelfServiceOrder.class))).thenAnswer(inv -> {
            SelfServiceOrder o = inv.getArgument(0); o.setId(4L); return o;
        });

        service.submitOrder("test-session-token", request);

        verify(notificationService).notifyNewOrder(any(Order.class));
        verify(orderEventBroadcaster).broadcastOrderPlaced(any(Order.class));
        verify(ownerNotificationService).notifyNewOrder(any(Order.class));
        verify(orderEventPublisher).publishOrderCreated(any(Order.class), eq("SELF_SERVICE"));
    }

    // ==================== Tests 30-34: Order Status & Settings ====================

    @Test
    void getOrderStatus_found_returnsOrder() {
        SelfServiceOrder ssOrder = SelfServiceOrder.builder().id(1L).build();
        when(selfServiceOrderRepository.findByOrderId(10L)).thenReturn(Optional.of(ssOrder));

        SelfServiceOrder result = service.getOrderStatus(10L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getOrderStatus_notFound_throwsException() {
        when(selfServiceOrderRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getOrderStatus(99L));
    }

    @Test
    void getOrderStatusWithSessionValidation_wrongSession_throwsException() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSession session1 = createValidSession(1L, restaurant, createTable(1L));
        SelfServiceSession session2 = createValidSession(2L, restaurant, createTable(2L));
        session2.setSessionToken("other-token");

        SelfServiceOrder ssOrder = SelfServiceOrder.builder().id(1L).session(session2).build();

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("test-session-token"))
                .thenReturn(Optional.of(session1));
        when(selfServiceOrderRepository.findByOrderId(10L)).thenReturn(Optional.of(ssOrder));

        assertThrows(RuntimeException.class,
                () -> service.getOrderStatusWithSessionValidation("test-session-token", 10L));
    }

    @Test
    void saveSettings_newSettings_createsRecord() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSettings newSettings = SelfServiceSettings.builder()
                .enabled(true).allowTakeaway(true).allowDineIn(true)
                .minimumOrderAmount(BigDecimal.ZERO).estimatedPrepTimeMinutes(20).build();

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.empty());
        when(settingsRepository.save(any(SelfServiceSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SelfServiceSettings result = service.saveSettings(1L, newSettings);

        assertNotNull(result);
        assertEquals(restaurant, result.getRestaurant());
        assertTrue(result.getEnabled());
        verify(settingsRepository).save(any(SelfServiceSettings.class));
    }

    @Test
    void saveSettings_existingSettings_updates() {
        Restaurant restaurant = createRestaurant(1L);
        SelfServiceSettings existing = SelfServiceSettings.builder()
                .id(1L).restaurant(restaurant).enabled(false).estimatedPrepTimeMinutes(10).build();
        SelfServiceSettings updated = SelfServiceSettings.builder()
                .enabled(true).allowTakeaway(true).estimatedPrepTimeMinutes(25).build();

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(SelfServiceSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SelfServiceSettings result = service.saveSettings(1L, updated);

        assertTrue(result.getEnabled());
        assertEquals(25, result.getEstimatedPrepTimeMinutes());
        verify(settingsRepository).save(existing);
    }
}
