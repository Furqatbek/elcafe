package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;

import com.elcafe.modules.bundle.entity.Bundle;
import com.elcafe.modules.bundle.repository.BundleRepository;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.kitchen.repository.KitchenOrderRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.dto.pos.CreatePOSOrderRequest;
import com.elcafe.modules.order.dto.pos.POSOrderResponse;
import com.elcafe.modules.order.dto.pos.POSProductAvailabilityDTO;
import com.elcafe.modules.order.entity.DeliveryInfo;
import com.elcafe.modules.marketing.event.OrderCompletionEvents;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.promotion.repository.CouponCodeRepository;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import com.elcafe.modules.promotion.repository.PromotionUsageRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class POSOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BundleRepository bundleRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private InventoryProductIngredientRepository productIngredientRepository;

    @Mock
    private KitchenOrderRepository kitchenOrderRepository;

    @Mock
    private RestaurantTableRepository restaurantTableRepository;

    @Mock
    private DailyOrderSequenceService dailyOrderSequenceService;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionUsageRepository promotionUsageRepository;

    @Mock
    private CouponCodeRepository couponCodeRepository;

    @Mock
    private POSTableService posTableService;
    @Mock
    private com.elcafe.modules.financial.service.RevenueService revenueService;

    @Mock
    private OrderCompletionEvents orderCompletionEvents;
    @Mock
    private com.elcafe.modules.menu.service.PackagingService packagingService;
    @Mock
    private com.elcafe.modules.pos.shift.service.ShiftManagementService shiftManagementService;
    @Mock
    private com.elcafe.modules.pos.shift.service.ShiftEnforcementService shiftEnforcementService;
    @Mock
    private com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository employeeShiftRepository;

    @InjectMocks
    private POSOrderService posOrderService;

    private Restaurant restaurant;
    private Customer customer;
    private Product product;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);

        customer = new Customer();
        customer.setId(1L);
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setPhone("+998901234567");
        customer.setEmail("john@test.com");

        product = new Product();
        product.setId(1L);
        product.setName("Latte");
        product.setPrice(BigDecimal.valueOf(15000));
        product.setInStock(true);
    }

    // ==================== Helper methods ====================

    private CreatePOSOrderRequest.OrderItemRequest buildItemRequest(Long productId, int qty, BigDecimal price) {
        return CreatePOSOrderRequest.OrderItemRequest.builder()
                .productId(productId)
                .quantity(qty)
                .price(price)
                .isBundle(false)
                .isFreeItem(false)
                .build();
    }

    private CreatePOSOrderRequest buildDineInRequest() {
        CreatePOSOrderRequest.CustomerInfo customerInfo = CreatePOSOrderRequest.CustomerInfo.builder()
                .name("John Doe")
                .phone("+998901234567")
                .email("john@test.com")
                .build();

        CreatePOSOrderRequest.DineInInfo dineInInfo = CreatePOSOrderRequest.DineInInfo.builder()
                .tableNumber("T1")
                .tableIds(List.of(1L))
                .guestCount(2)
                .build();

        return CreatePOSOrderRequest.builder()
                .restaurantId(1L)
                .orderType(CreatePOSOrderRequest.OrderType.DINE_IN)
                .customerInfo(customerInfo)
                .items(List.of(buildItemRequest(1L, 2, BigDecimal.valueOf(15000))))
                .dineInInfo(dineInInfo)
                .subtotal(BigDecimal.valueOf(30000))
                .tax(BigDecimal.valueOf(3000))
                .total(BigDecimal.valueOf(33000))
                .discount(BigDecimal.ZERO)
                .build();
    }

    private CreatePOSOrderRequest buildDeliveryRequest() {
        CreatePOSOrderRequest.CustomerInfo customerInfo = CreatePOSOrderRequest.CustomerInfo.builder()
                .name("Jane Smith")
                .phone("+998907654321")
                .email("jane@test.com")
                .build();

        CreatePOSOrderRequest.DeliveryInfo deliveryInfo = CreatePOSOrderRequest.DeliveryInfo.builder()
                .street("456 Oak Ave")
                .city("Tashkent")
                .state("Tashkent")
                .zipCode("100000")
                .deliveryInstructions("Ring bell twice")
                .build();

        return CreatePOSOrderRequest.builder()
                .restaurantId(1L)
                .orderType(CreatePOSOrderRequest.OrderType.DELIVERY)
                .customerInfo(customerInfo)
                .items(List.of(buildItemRequest(1L, 1, BigDecimal.valueOf(15000))))
                .deliveryInfo(deliveryInfo)
                .subtotal(BigDecimal.valueOf(15000))
                .tax(BigDecimal.valueOf(1500))
                .deliveryFee(BigDecimal.valueOf(5000))
                .total(BigDecimal.valueOf(21500))
                .discount(BigDecimal.ZERO)
                .build();
    }

    private CreatePOSOrderRequest buildTakeawayRequest() {
        CreatePOSOrderRequest.CustomerInfo customerInfo = CreatePOSOrderRequest.CustomerInfo.builder()
                .name("Bob Wilson")
                .phone("+998909999999")
                .build();

        return CreatePOSOrderRequest.builder()
                .restaurantId(1L)
                .orderType(CreatePOSOrderRequest.OrderType.TAKEAWAY)
                .customerInfo(customerInfo)
                .items(List.of(buildItemRequest(1L, 1, BigDecimal.valueOf(15000))))
                .subtotal(BigDecimal.valueOf(15000))
                .tax(BigDecimal.valueOf(1500))
                .total(BigDecimal.valueOf(16500))
                .discount(BigDecimal.ZERO)
                .build();
    }

    @Test
    void createOrder_autoPay_marksCompletedAndPublishesCompletionEvent() {
        stubCommonCreateOrderDeps();
        CreatePOSOrderRequest request = buildDineInRequest();
        request.setPaymentMethod("CASH");

        var result = posOrderService.createOrder(request);

        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.COMPLETED, result.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.elcafe.modules.order.enums.PaymentStatus.COMPLETED, result.getPaymentStatus());
        verify(revenueService).recordOrderRevenue(any(Order.class));
        // Born settled+paid: the auto-pay branch is a qualifying moment for the completion chain.
        verify(orderCompletionEvents).publishIfQualified(any(Order.class));
    }

    private void stubCommonCreateOrderDeps() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(dailyOrderSequenceService.generateNextOrderNumber()).thenReturn("ORD-20260329-0001");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) {
                o.setId(1L);
            }
            if (o.getCreatedAt() == null) {
                o.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            }
            return o;
        });
        when(inventoryService.checkIngredientAvailability(any(Order.class))).thenReturn(true);
        doNothing().when(inventoryService).deductIngredientsForOrder(any(Order.class));
        doNothing().when(notificationService).notifyNewOrder(any(Order.class));
    }

    private Order buildOrderForResponse(OrderStatus status, OrderType orderType) {
        Order order = Order.builder()
                .orderNumber("ORD-20260329-0001")
                .restaurant(restaurant)
                .customer(customer)
                .status(status)
                .orderType(orderType)
                .subtotal(BigDecimal.valueOf(30000))
                .tax(BigDecimal.valueOf(3000))
                .deliveryFee(BigDecimal.ZERO)
                .serviceFeePercent(BigDecimal.ZERO)
                .serviceFee(BigDecimal.ZERO)
                .entryFee(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(33000))
                .items(new ArrayList<>())
                .build();
        order.setId(1L);
        order.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        order.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return order;
    }

    // ==================== createOrder ====================

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        @Test
        @DisplayName("1. dine-in with items - creates order successfully")
        void createOrder_dineIn_withItems_success() {
            CreatePOSOrderRequest request = buildDineInRequest();
            stubCommonCreateOrderDeps();
            when(customerRepository.findByPhoneAndRestaurantId("+998901234567", 1L)).thenReturn(Optional.of(customer));

            POSOrderResponse response = posOrderService.createOrder(request);

            assertNotNull(response);
            assertEquals("ORD-20260329-0001", response.getOrderNumber());
            assertEquals(OrderStatus.NEW, response.getStatus());
            assertEquals("DINE_IN", response.getOrderType());
            assertEquals("John Doe", response.getCustomerName());
            assertNotNull(response.getItems());
            assertFalse(response.getItems().isEmpty());

            verify(orderRepository).save(any(Order.class));
            verify(inventoryService).checkIngredientAvailability(any(Order.class));
            verify(inventoryService).deductIngredientsForOrder(any(Order.class));
            verify(notificationService).notifyNewOrder(any(Order.class));
            verify(posTableService).assignTablesToOrder(any(Order.class), eq(List.of(1L)));
        }

        @Test
        @DisplayName("2. delivery with delivery info - creates order and sets delivery address")
        void createOrder_delivery_withDeliveryInfo_success() {
            CreatePOSOrderRequest request = buildDeliveryRequest();
            stubCommonCreateOrderDeps();
            Customer deliveryCustomer = new Customer();
            deliveryCustomer.setId(2L);
            deliveryCustomer.setFirstName("Jane");
            deliveryCustomer.setLastName("Smith");
            deliveryCustomer.setPhone("+998907654321");
            when(customerRepository.findByPhoneAndRestaurantId("+998907654321", 1L)).thenReturn(Optional.of(deliveryCustomer));

            POSOrderResponse response = posOrderService.createOrder(request);

            assertNotNull(response);
            assertEquals("DELIVERY", response.getOrderType());
            assertNotNull(response.getDeliveryAddress());
            assertEquals("456 Oak Ave", response.getDeliveryAddress().getStreet());
            assertEquals("Tashkent", response.getDeliveryAddress().getCity());
            assertEquals("Tashkent", response.getDeliveryAddress().getState());
            assertEquals("100000", response.getDeliveryAddress().getZipCode());
            assertEquals("Ring bell twice", response.getDeliveryAddress().getDeliveryInstructions());
            assertNotNull(response.getEstimatedDeliveryTime());
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("3. takeaway - creates order without table or delivery info")
        void createOrder_takeaway_success() {
            CreatePOSOrderRequest request = buildTakeawayRequest();
            stubCommonCreateOrderDeps();
            Customer takeawayCustomer = new Customer();
            takeawayCustomer.setId(3L);
            takeawayCustomer.setFirstName("Bob");
            takeawayCustomer.setLastName("Wilson");
            takeawayCustomer.setPhone("+998909999999");
            when(customerRepository.findByPhoneAndRestaurantId("+998909999999", 1L)).thenReturn(Optional.of(takeawayCustomer));

            POSOrderResponse response = posOrderService.createOrder(request);

            assertNotNull(response);
            assertEquals("TAKEAWAY", response.getOrderType());
            assertNull(response.getDeliveryAddress());
            assertNull(response.getDineInInfo());
            verify(orderRepository).save(any(Order.class));
            verify(posTableService, never()).assignTablesToOrder(any(), any());
        }

        @Test
        @DisplayName("4. with customer info - finds existing customer by phone")
        void createOrder_withCustomer_findsExisting() {
            CreatePOSOrderRequest request = buildDineInRequest();
            stubCommonCreateOrderDeps();
            when(customerRepository.findByPhoneAndRestaurantId("+998901234567", 1L)).thenReturn(Optional.of(customer));

            POSOrderResponse response = posOrderService.createOrder(request);

            assertNotNull(response);
            assertEquals("John Doe", response.getCustomerName());
            assertEquals("+998901234567", response.getCustomerPhone());
            verify(customerRepository).findByPhoneAndRestaurantId("+998901234567", 1L);
            verify(customerRepository, never()).save(any(Customer.class));
        }

        @Test
        @DisplayName("5. dine-in without customer info - walk-in guest (null customer)")
        void createOrder_withoutCustomer_walkInGuest() {
            CreatePOSOrderRequest request = buildDineInRequest();
            request.setCustomerInfo(null);
            stubCommonCreateOrderDeps();

            POSOrderResponse response = posOrderService.createOrder(request);

            assertNotNull(response);
            assertNull(response.getCustomerName());
            assertNull(response.getCustomerPhone());
            verify(customerRepository, never()).findByPhoneAndRestaurantId(anyString(), any());
        }

        @Test
        @DisplayName("6. with bundle item - resolves bundle from repository")
        void createOrder_withBundleItem_success() {
            Bundle bundle = new Bundle();
            bundle.setId(10L);
            bundle.setName("Breakfast Combo");

            CreatePOSOrderRequest.OrderItemRequest bundleItem = CreatePOSOrderRequest.OrderItemRequest.builder()
                    .productId(null)
                    .bundleId(10L)
                    .isBundle(true)
                    .quantity(1)
                    .price(BigDecimal.valueOf(25000))
                    .isFreeItem(false)
                    .build();

            CreatePOSOrderRequest request = buildDineInRequest();
            request.setItems(List.of(bundleItem));
            stubCommonCreateOrderDeps();
            when(customerRepository.findByPhoneAndRestaurantId("+998901234567", 1L)).thenReturn(Optional.of(customer));
            when(bundleRepository.findById(10L)).thenReturn(Optional.of(bundle));

            POSOrderResponse response = posOrderService.createOrder(request);

            assertNotNull(response);
            assertFalse(response.getItems().isEmpty());
            assertEquals("Breakfast Combo", response.getItems().get(0).getProductName());
            verify(bundleRepository).findById(10L);
        }

        @Test
        @DisplayName("7. with modifiers - adds modifier info to order item")
        void createOrder_withModifiers_success() {
            CreatePOSOrderRequest.ModifierInfo modifier1 = CreatePOSOrderRequest.ModifierInfo.builder()
                    .addOnId(100L)
                    .name("Extra Shot")
                    .price(BigDecimal.valueOf(2000))
                    .quantity(1)
                    .build();

            CreatePOSOrderRequest.ModifierInfo modifier2 = CreatePOSOrderRequest.ModifierInfo.builder()
                    .addOnId(101L)
                    .name("Oat Milk")
                    .price(BigDecimal.valueOf(1500))
                    .quantity(1)
                    .build();

            CreatePOSOrderRequest.OrderItemRequest itemWithModifiers = CreatePOSOrderRequest.OrderItemRequest.builder()
                    .productId(1L)
                    .quantity(1)
                    .price(BigDecimal.valueOf(15000))
                    .modifiers(List.of(modifier1, modifier2))
                    .isBundle(false)
                    .isFreeItem(false)
                    .build();

            CreatePOSOrderRequest request = buildDineInRequest();
            request.setItems(List.of(itemWithModifiers));
            stubCommonCreateOrderDeps();
            when(customerRepository.findByPhoneAndRestaurantId("+998901234567", 1L)).thenReturn(Optional.of(customer));

            POSOrderResponse response = posOrderService.createOrder(request);

            assertNotNull(response);
            assertFalse(response.getItems().isEmpty());
            POSOrderResponse.OrderItemResponse itemResponse = response.getItems().get(0);
            assertNotNull(itemResponse.getModifiers());
            assertEquals(2, itemResponse.getModifiers().size());
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("8. restaurant not found - throws a typed exception")
        void createOrder_restaurantNotFound_throws() {
            CreatePOSOrderRequest request = buildDineInRequest();
            request.setRestaurantId(99L);
            when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> posOrderService.createOrder(request));

            assertTrue(ex.getMessage().contains("Restaurant not found"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("9. insufficient inventory - throws a typed exception")
        void createOrder_insufficientInventory_throws() {
            CreatePOSOrderRequest request = buildDineInRequest();
            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
            when(dailyOrderSequenceService.generateNextOrderNumber()).thenReturn("ORD-20260329-0001");
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(customerRepository.findByPhoneAndRestaurantId("+998901234567", 1L)).thenReturn(Optional.of(customer));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order o = invocation.getArgument(0);
                if (o.getId() == null) o.setId(1L);
                return o;
            });
            when(inventoryService.checkIngredientAvailability(any(Order.class))).thenReturn(false);
            when(inventoryService.getMissingIngredients(anyLong(), any(Integer.class)))
                    .thenReturn(List.of("Milk (need: 500 ml, have: 100 ml)"));

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> posOrderService.createOrder(request));

            assertTrue(ex.getMessage().contains("Insufficient inventory"));
            verify(inventoryService, never()).deductIngredientsForOrder(any(Order.class));
        }

        @Test
        @DisplayName("10. multiple distinct items - all persisted (regression for HashSet bug)")
        void createOrder_multipleItems_allPersisted() {
            Product product2 = new Product();
            product2.setId(2L);
            product2.setName("Cappuccino");
            product2.setPrice(BigDecimal.valueOf(12000));
            product2.setInStock(true);

            Product product3 = new Product();
            product3.setId(3L);
            product3.setName("Espresso");
            product3.setPrice(BigDecimal.valueOf(8000));
            product3.setInStock(true);

            CreatePOSOrderRequest.OrderItemRequest item1 = buildItemRequest(1L, 2, BigDecimal.valueOf(15000));
            CreatePOSOrderRequest.OrderItemRequest item2 = buildItemRequest(2L, 1, BigDecimal.valueOf(12000));
            CreatePOSOrderRequest.OrderItemRequest item3 = buildItemRequest(3L, 3, BigDecimal.valueOf(8000));

            CreatePOSOrderRequest request = buildDineInRequest();
            request.setItems(List.of(item1, item2, item3));
            request.setSubtotal(BigDecimal.valueOf(66000));
            request.setTotal(BigDecimal.valueOf(66000));

            stubCommonCreateOrderDeps();
            when(customerRepository.findByPhoneAndRestaurantId("+998901234567", 1L)).thenReturn(Optional.of(customer));
            when(productRepository.findById(2L)).thenReturn(Optional.of(product2));
            when(productRepository.findById(3L)).thenReturn(Optional.of(product3));

            POSOrderResponse response = posOrderService.createOrder(request);

            assertNotNull(response);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertTrue(captor.getValue().getItems().size() >= 3,
                    "All 3 distinct items must be persisted - regression for HashSet bug");
        }
    }

    // ==================== getOpenDineInOrders ====================

    @Nested
    @DisplayName("getOpenDineInOrders")
    class GetOpenDineInOrdersTests {

        @Test
        @DisplayName("11. returns active dine-in orders filtered by unpaid status")
        void getOpenDineInOrders_returnsActiveOrders() {
            Order order1 = buildOrderForResponse(OrderStatus.NEW, OrderType.DINE_IN);
            order1.setPaymentStatus(PaymentStatus.PENDING);
            RestaurantTable table1 = new RestaurantTable();
            table1.setId(1L);
            table1.setTableNumber("T1");
            order1.setDiningTable(table1);
            OrderItem item1 = OrderItem.builder()
                    .id(1L)
                    .productId(1L)
                    .productName("Latte")
                    .quantity(2)
                    .unitPrice(BigDecimal.valueOf(15000))
                    .totalPrice(BigDecimal.valueOf(30000))
                    .build();
            order1.getItems().add(item1);

            Order order2 = buildOrderForResponse(OrderStatus.PREPARING, OrderType.DINE_IN);
            order2.setId(2L);
            order2.setOrderNumber("ORD-20260329-0002");
            order2.setPaymentStatus(PaymentStatus.PENDING);
            RestaurantTable table2 = new RestaurantTable();
            table2.setId(2L);
            table2.setTableNumber("T2");
            order2.setDiningTable(table2);

            when(orderRepository.findByRestaurant_IdAndDiningTableIsNotNullAndStatusIn(
                    eq(1L), any())).thenReturn(List.of(order1, order2));

            List<POSOrderResponse> result = posOrderService.getOpenDineInOrders(1L);

            assertEquals(2, result.size());
            assertEquals("ORD-20260329-0001", result.get(0).getOrderNumber());
            assertEquals("DINE_IN", result.get(0).getOrderType());
            assertEquals("ORD-20260329-0002", result.get(1).getOrderNumber());
        }

        @Test
        @DisplayName("12. returns empty list when no open orders exist")
        void getOpenDineInOrders_emptyList() {
            when(orderRepository.findByRestaurant_IdAndDiningTableIsNotNullAndStatusIn(
                    eq(1L), any())).thenReturn(Collections.emptyList());

            List<POSOrderResponse> result = posOrderService.getOpenDineInOrders(1L);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== getProductAvailability ====================

    @Nested
    @DisplayName("getProductAvailability")
    class GetProductAvailabilityTests {

        @Test
        @DisplayName("13. product available - returns AVAILABLE status with servings count")
        void getProductAvailability_available() {
            Ingredient milk = new Ingredient();
            milk.setId(1L);
            milk.setName("Milk");
            milk.setUnit("ml");
            milk.setCurrentStock(BigDecimal.valueOf(5000));

            ProductIngredient pi = new ProductIngredient();
            pi.setIngredient(milk);
            pi.setQuantityRequired(BigDecimal.valueOf(200));
            pi.setOptional(false);

            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productIngredientRepository.findByProductIdWithIngredients(1L))
                    .thenReturn(List.of(pi));

            POSProductAvailabilityDTO result = posOrderService.getProductAvailability(1L, 1L);

            assertNotNull(result);
            assertEquals(1L, result.getProductId());
            assertEquals("Latte", result.getProductName());
            assertTrue(result.isAvailable());
            assertEquals("AVAILABLE", result.getStockStatus());
            // 5000 / 200 = 25 servings
            assertEquals(25, result.getMaxQuantityAvailable());
            assertFalse(result.getIngredientDetails().isEmpty());
            assertEquals("Milk", result.getIngredientDetails().get(0).getIngredientName());
            assertTrue(result.getIngredientDetails().get(0).isSufficient());
        }

        @Test
        @DisplayName("14. product out of stock - returns OUT_OF_STOCK status")
        void getProductAvailability_outOfStock() {
            Ingredient milk = new Ingredient();
            milk.setId(1L);
            milk.setName("Milk");
            milk.setUnit("ml");
            milk.setCurrentStock(BigDecimal.ZERO);

            ProductIngredient pi = new ProductIngredient();
            pi.setIngredient(milk);
            pi.setQuantityRequired(BigDecimal.valueOf(200));
            pi.setOptional(false);

            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productIngredientRepository.findByProductIdWithIngredients(1L))
                    .thenReturn(List.of(pi));

            POSProductAvailabilityDTO result = posOrderService.getProductAvailability(1L, 1L);

            assertNotNull(result);
            assertFalse(result.isAvailable());
            assertEquals("OUT_OF_STOCK", result.getStockStatus());
            assertEquals(0, result.getMaxQuantityAvailable());
            assertFalse(result.getIngredientDetails().get(0).isSufficient());
        }
    }

    // ==================== getOrderById ====================

    @Nested
    @DisplayName("getOrderById")
    class GetOrderByIdTests {

        @Test
        @DisplayName("15. order found - returns mapped response with correct fields")
        void getOrderById_success() {
            Order order = buildOrderForResponse(OrderStatus.PREPARING, OrderType.DINE_IN);
            RestaurantTable table = new RestaurantTable();
            table.setId(1L);
            table.setTableNumber("T1");
            order.setDiningTable(table);
            order.setGuestCount(3);

            OrderItem item = OrderItem.builder()
                    .id(1L)
                    .productId(1L)
                    .productName("Latte")
                    .quantity(2)
                    .unitPrice(BigDecimal.valueOf(15000))
                    .totalPrice(BigDecimal.valueOf(30000))
                    .build();
            order.getItems().add(item);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            POSOrderResponse response = posOrderService.getOrderById(1L);

            assertNotNull(response);
            assertEquals(1L, response.getId());
            assertEquals("ORD-20260329-0001", response.getOrderNumber());
            assertEquals(OrderStatus.PREPARING, response.getStatus());
            assertEquals("DINE_IN", response.getOrderType());
            assertEquals("John Doe", response.getCustomerName());
            assertEquals("+998901234567", response.getCustomerPhone());
            assertEquals(1, response.getItems().size());
            assertEquals("Latte", response.getItems().get(0).getProductName());
            assertNotNull(response.getDineInInfo());
            assertEquals("T1", response.getDineInInfo().getTableNumber());
            assertEquals(3, response.getDineInInfo().getGuestCount());
        }

        @Test
        @DisplayName("16. order not found - throws a typed exception")
        void getOrderById_notFound_throws() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> posOrderService.getOrderById(99L));

            assertTrue(ex.getMessage().contains("Order not found"));
            assertTrue(ex.getMessage().contains("99"));
        }
    }

    // ==================== mapToResponse ====================

    @Nested
    @DisplayName("mapToResponse")
    class MapToResponseTests {

        @Test
        @DisplayName("17. dine-in with table info - includes dineInInfo in response")
        void mapToResponse_dineIn_withTableInfo() {
            Order order = buildOrderForResponse(OrderStatus.NEW, OrderType.DINE_IN);
            RestaurantTable table = new RestaurantTable();
            table.setId(1L);
            table.setTableNumber("T1");
            order.setDiningTable(table);
            order.setGuestCount(4);

            OrderItem item = OrderItem.builder()
                    .id(1L)
                    .productId(1L)
                    .productName("Latte")
                    .quantity(1)
                    .unitPrice(BigDecimal.valueOf(15000))
                    .totalPrice(BigDecimal.valueOf(15000))
                    .build();
            order.getItems().add(item);

            POSOrderResponse response = posOrderService.mapToResponse(order, "DINE_IN");

            assertNotNull(response);
            assertEquals("DINE_IN", response.getOrderType());
            assertEquals(1L, response.getRestaurantId());
            assertNotNull(response.getDineInInfo());
            assertEquals("T1", response.getDineInInfo().getTableNumber());
            assertEquals(4, response.getDineInInfo().getGuestCount());
            assertNotNull(response.getDineInInfo().getTableIds());
            assertNull(response.getDeliveryAddress());
        }

        @Test
        @DisplayName("18. delivery with address - includes delivery address in response")
        void mapToResponse_delivery_withAddress() {
            Order order = buildOrderForResponse(OrderStatus.NEW, OrderType.DELIVERY);
            order.setDiningTable(null);

            DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                    .address("456 Oak Ave")
                    .city("Tashkent")
                    .state("Tashkent")
                    .zipCode("100000")
                    .deliveryInstructions("Ring bell twice")
                    .estimatedDeliveryTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(45))
                    .build();
            deliveryInfo.setOrder(order);
            order.setDeliveryInfo(deliveryInfo);

            OrderItem item = OrderItem.builder()
                    .id(1L)
                    .productId(1L)
                    .productName("Latte")
                    .quantity(1)
                    .unitPrice(BigDecimal.valueOf(15000))
                    .totalPrice(BigDecimal.valueOf(15000))
                    .build();
            order.getItems().add(item);

            POSOrderResponse response = posOrderService.mapToResponse(order, "DELIVERY");

            assertNotNull(response);
            assertEquals("DELIVERY", response.getOrderType());
            assertNotNull(response.getDeliveryAddress());
            assertEquals("456 Oak Ave", response.getDeliveryAddress().getStreet());
            assertEquals("Tashkent", response.getDeliveryAddress().getCity());
            assertEquals("Tashkent", response.getDeliveryAddress().getState());
            assertEquals("100000", response.getDeliveryAddress().getZipCode());
            assertEquals("Ring bell twice", response.getDeliveryAddress().getDeliveryInstructions());
            assertNotNull(response.getEstimatedDeliveryTime());
            assertNull(response.getDineInInfo());
        }

        @Test
        @DisplayName("19. null customer - handled gracefully without NPE")
        void mapToResponse_nullCustomer_handledGracefully() {
            Order order = buildOrderForResponse(OrderStatus.NEW, OrderType.TAKEAWAY);
            order.setCustomer(null);

            OrderItem item = OrderItem.builder()
                    .id(1L)
                    .productId(1L)
                    .productName("Latte")
                    .quantity(1)
                    .unitPrice(BigDecimal.valueOf(15000))
                    .totalPrice(BigDecimal.valueOf(15000))
                    .build();
            order.getItems().add(item);

            POSOrderResponse response = posOrderService.mapToResponse(order, "TAKEAWAY");

            assertNotNull(response);
            assertNull(response.getCustomerName());
            assertNull(response.getCustomerPhone());
            assertEquals("TAKEAWAY", response.getOrderType());
            assertEquals(OrderStatus.NEW, response.getStatus());
            assertEquals(0, BigDecimal.valueOf(33000).compareTo(response.getTotal()));
        }
    }

    // ==================== getOrderTypeString ====================

    @Nested
    @DisplayName("getOrderTypeString")
    class GetOrderTypeStringTests {

        @Test
        @DisplayName("20. infers order type from orderType field and falls back to context")
        void getOrderTypeString_infersFromContext() {
            // When orderType is explicitly set, use it directly
            Order dineInOrder = buildOrderForResponse(OrderStatus.NEW, OrderType.DINE_IN);
            assertEquals("DINE_IN", posOrderService.getOrderTypeString(dineInOrder));

            Order deliveryOrder = buildOrderForResponse(OrderStatus.NEW, OrderType.DELIVERY);
            assertEquals("DELIVERY", posOrderService.getOrderTypeString(deliveryOrder));

            Order takeawayOrder = buildOrderForResponse(OrderStatus.NEW, OrderType.TAKEAWAY);
            assertEquals("TAKEAWAY", posOrderService.getOrderTypeString(takeawayOrder));

            // When orderType is null, infer from diningTable presence
            Order nullTypeWithTable = buildOrderForResponse(OrderStatus.NEW, null);
            RestaurantTable table = new RestaurantTable();
            table.setId(1L);
            table.setTableNumber("T1");
            nullTypeWithTable.setDiningTable(table);
            assertEquals("DINE_IN", posOrderService.getOrderTypeString(nullTypeWithTable));

            // When orderType is null and has deliveryInfo, infer DELIVERY
            Order nullTypeWithDelivery = buildOrderForResponse(OrderStatus.NEW, null);
            nullTypeWithDelivery.setDiningTable(null);
            DeliveryInfo deliveryInfo = new DeliveryInfo();
            deliveryInfo.setAddress("Test");
            nullTypeWithDelivery.setDeliveryInfo(deliveryInfo);
            assertEquals("DELIVERY", posOrderService.getOrderTypeString(nullTypeWithDelivery));

            // When orderType is null with no table and no delivery, infer TAKEAWAY
            Order nullTypeTakeaway = buildOrderForResponse(OrderStatus.NEW, null);
            nullTypeTakeaway.setDiningTable(null);
            nullTypeTakeaway.setDeliveryInfo(null);
            assertEquals("TAKEAWAY", posOrderService.getOrderTypeString(nullTypeTakeaway));
        }
    }

    @Nested
    @DisplayName("attachCustomer")
    class AttachCustomerTests {

        @Test
        @DisplayName("attaches customer by qrCode")
        void attachByQrCode() {
            Order order = buildOrderForResponse(OrderStatus.NEW, OrderType.DINE_IN);
            order.setId(1L);
            Customer target = Customer.builder().id(7L).firstName("Alice").lastName("Doe")
                    .phone("+998900000000").qrCode("CST-ABC123").build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(customerRepository.findByQrCode("CST-ABC123")).thenReturn(Optional.of(target));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            com.elcafe.modules.order.dto.pos.AttachCustomerRequest req =
                    com.elcafe.modules.order.dto.pos.AttachCustomerRequest.builder()
                            .qrCode("CST-ABC123").build();

            POSOrderResponse response = posOrderService.attachCustomer(1L, req);

            assertNotNull(response);
            assertEquals(target, order.getCustomer());
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("attaches customer by phone")
        void attachByPhone() {
            Order order = buildOrderForResponse(OrderStatus.NEW, OrderType.TAKEAWAY);
            order.setId(2L);
            Customer target = Customer.builder().id(11L).firstName("Bob").lastName("Smith")
                    .phone("+998901111111").qrCode("CST-XYZ").build();

            when(orderRepository.findById(2L)).thenReturn(Optional.of(order));
            when(customerRepository.findByPhoneAndRestaurantId("+998901111111", 1L)).thenReturn(Optional.of(target));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            posOrderService.attachCustomer(2L,
                    com.elcafe.modules.order.dto.pos.AttachCustomerRequest.builder()
                            .phone("+998901111111").build());

            assertEquals(target, order.getCustomer());
        }

        @Test
        @DisplayName("rejects when order is settled (DELIVERED/COMPLETED/CANCELLED)")
        void rejectsSettledOrder() {
            for (OrderStatus terminal : new OrderStatus[]{OrderStatus.DELIVERED, OrderStatus.COMPLETED, OrderStatus.CANCELLED}) {
                Order order = buildOrderForResponse(terminal, OrderType.TAKEAWAY);
                order.setId(3L);
                when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

                BadRequestException ex = assertThrows(BadRequestException.class,
                        () -> posOrderService.attachCustomer(3L,
                                com.elcafe.modules.order.dto.pos.AttachCustomerRequest.builder()
                                        .customerId(99L).build()));
                assertTrue(ex.getMessage().contains(terminal.name()));
                verify(customerRepository, never()).findById(any());
            }
        }

        @Test
        @DisplayName("rejects when order does not exist")
        void rejectsMissingOrder() {
            when(orderRepository.findById(404L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    posOrderService.attachCustomer(404L,
                            com.elcafe.modules.order.dto.pos.AttachCustomerRequest.builder()
                                    .qrCode("CST-NOPE").build()));
        }
    }
}
