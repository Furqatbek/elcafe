package com.elcafe.modules.order.service;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.dto.pos.CreatePOSOrderRequest;
import com.elcafe.modules.order.dto.pos.POSOrderResponse;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.promotion.repository.CouponCodeRepository;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import com.elcafe.modules.promotion.repository.PromotionUsageRepository;
import com.elcafe.modules.bundle.repository.BundleRepository;
import com.elcafe.modules.pos.kitchen.repository.KitchenOrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createProduct;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createCustomer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class POSOrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private BundleRepository bundleRepository;
    @Mock private NotificationService notificationService;
    @Mock private InventoryService inventoryService;
    @Mock private InventoryProductIngredientRepository productIngredientRepository;
    @Mock private KitchenOrderRepository kitchenOrderRepository;
    @Mock private RestaurantTableRepository restaurantTableRepository;
    @Mock private DailyOrderSequenceService dailyOrderSequenceService;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PromotionUsageRepository promotionUsageRepository;
    @Mock private CouponCodeRepository couponCodeRepository;
    @Mock private POSTableService posTableService;

    @InjectMocks private POSOrderService posOrderService;

    private Restaurant restaurant;
    private Product product;

    @BeforeEach
    void setUp() {
        restaurant = createRestaurant();
        product = createProduct(1L, "Steak", BigDecimal.valueOf(80000));
    }

    private CreatePOSOrderRequest buildDineInRequest() {
        CreatePOSOrderRequest req = new CreatePOSOrderRequest();
        req.setRestaurantId(1L);
        req.setOrderType(CreatePOSOrderRequest.OrderType.DINE_IN);
        req.setSubtotal(BigDecimal.valueOf(80000));
        req.setTotal(BigDecimal.valueOf(80000));
        req.setDiscount(BigDecimal.ZERO);
        req.setTax(BigDecimal.ZERO);
        req.setDeliveryFee(BigDecimal.ZERO);
        req.setServiceFee(BigDecimal.ZERO);
        req.setServiceFeePercent(BigDecimal.ZERO);
        req.setEntryFee(BigDecimal.ZERO);

        CreatePOSOrderRequest.OrderItemRequest item = new CreatePOSOrderRequest.OrderItemRequest();
        item.setProductId(1L);
        item.setQuantity(1);
        item.setPrice(BigDecimal.valueOf(80000));
        req.setItems(List.of(item));

        return req;
    }

    private void stubCommonMocks() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(dailyOrderSequenceService.generateNextOrderNumber()).thenReturn("ORD-20260329-0001");
        when(inventoryService.checkIngredientAvailability(any(Order.class))).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            if (o.getId() == null) o.setId(1L);
            return o;
        });
    }

    // ==================== createOrder ====================

    @Test
    @DisplayName("Create dine-in order with items — success")
    void createOrder_dineIn_success() {
        stubCommonMocks();

        POSOrderResponse result = posOrderService.createOrder(buildDineInRequest());

        assertNotNull(result);
        assertEquals("ORD-20260329-0001", result.getOrderNumber());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("Create takeaway order — success")
    void createOrder_takeaway_success() {
        stubCommonMocks();
        CreatePOSOrderRequest req = buildDineInRequest();
        req.setOrderType(CreatePOSOrderRequest.OrderType.TAKEAWAY);

        POSOrderResponse result = posOrderService.createOrder(req);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Create order with customer — finds existing")
    void createOrder_withCustomer_findsExisting() {
        stubCommonMocks();
        Customer customer = createCustomer();
        when(customerRepository.findByPhone("+998901111111")).thenReturn(Optional.of(customer));

        CreatePOSOrderRequest req = buildDineInRequest();
        CreatePOSOrderRequest.CustomerInfo customerInfo = new CreatePOSOrderRequest.CustomerInfo();
        customerInfo.setPhone("+998901111111");
        customerInfo.setName("Test Customer");
        req.setCustomerInfo(customerInfo);

        POSOrderResponse result = posOrderService.createOrder(req);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Create order without customer — walk-in guest")
    void createOrder_noCustomer_walkIn() {
        stubCommonMocks();
        CreatePOSOrderRequest req = buildDineInRequest();
        req.setCustomerInfo(null);

        POSOrderResponse result = posOrderService.createOrder(req);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Restaurant not found — throws")
    void createOrder_restaurantNotFound_throws() {
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

        CreatePOSOrderRequest req = buildDineInRequest();
        req.setRestaurantId(99L);

        assertThrows(IllegalArgumentException.class,
                () -> posOrderService.createOrder(req));
    }

    @Test
    @DisplayName("Product not found — throws")
    void createOrder_productNotFound_throws() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        when(dailyOrderSequenceService.generateNextOrderNumber()).thenReturn("ORD-001");

        assertThrows(IllegalArgumentException.class,
                () -> posOrderService.createOrder(buildDineInRequest()));
    }

    @Test
    @DisplayName("Insufficient inventory — throws")
    void createOrder_insufficientInventory_throws() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(dailyOrderSequenceService.generateNextOrderNumber()).thenReturn("ORD-001");
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            if (o.getId() == null) o.setId(1L);
            return o;
        });
        when(inventoryService.checkIngredientAvailability(any(Order.class))).thenReturn(false);
        when(inventoryService.getMissingIngredients(anyLong(), any(Integer.class))).thenReturn(List.of("Missing: Sugar"));

        assertThrows(IllegalStateException.class,
                () -> posOrderService.createOrder(buildDineInRequest()));
    }

    @Test
    @DisplayName("Multiple items — all persisted (regression)")
    void createOrder_multipleItems_allPersisted() {
        stubCommonMocks();
        Product product2 = createProduct(2L, "Salad", BigDecimal.valueOf(30000));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product2));

        CreatePOSOrderRequest req = buildDineInRequest();
        CreatePOSOrderRequest.OrderItemRequest item2 = new CreatePOSOrderRequest.OrderItemRequest();
        item2.setProductId(2L);
        item2.setQuantity(2);
        item2.setPrice(BigDecimal.valueOf(30000));
        req.setItems(List.of(req.getItems().get(0), item2));
        req.setSubtotal(BigDecimal.valueOf(140000));
        req.setTotal(BigDecimal.valueOf(140000));

        posOrderService.createOrder(req);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertTrue(captor.getValue().getItems().size() >= 2,
                "All items must be persisted — regression for HashSet bug");
    }

    @Test
    @DisplayName("Order number generated correctly")
    void createOrder_orderNumberGenerated() {
        stubCommonMocks();

        posOrderService.createOrder(buildDineInRequest());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals("ORD-20260329-0001", captor.getValue().getOrderNumber());
    }

    @Test
    @DisplayName("Order status is NEW")
    void createOrder_statusIsNew() {
        stubCommonMocks();

        posOrderService.createOrder(buildDineInRequest());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(OrderStatus.NEW, captor.getValue().getStatus());
    }
}
