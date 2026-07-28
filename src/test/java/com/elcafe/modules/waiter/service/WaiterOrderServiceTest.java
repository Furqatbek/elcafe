package com.elcafe.modules.waiter.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.promotion.service.CouponValidationService;
import com.elcafe.modules.promotion.service.DiscountCalculationService;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.entity.RestaurantTable.TableStatus;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import com.elcafe.modules.waiter.dto.AddOrderItemRequest;
import com.elcafe.modules.waiter.dto.CreateOrderRequest;
import com.elcafe.modules.waiter.dto.UpdateOrderItemRequest;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterCommission;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaiterOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private WaiterRepository waiterRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private OrderEventService orderEventService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private DiscountCalculationService discountCalculationService;

    @Mock
    private CouponValidationService couponValidationService;

    @Mock
    private WaiterCommissionService waiterCommissionService;

    @Mock
    private com.elcafe.modules.order.service.DailyOrderSequenceService dailyOrderSequenceService;

    @Mock
    private com.elcafe.modules.waiter.event.OrderEventPublisher orderEventPublisher;

    @Mock
    private com.elcafe.modules.marketing.event.OrderCompletionEvents orderCompletionEvents;

    @InjectMocks
    private WaiterOrderService waiterOrderService;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private RestaurantTable table;
    private Waiter waiter;
    private Product product;
    private Product product2;
    private Customer customer;

    @BeforeEach
    void setUp() {
        table = createTable();
        waiter = createWaiter();
        product = createProduct(1L, "Espresso", BigDecimal.valueOf(5.00));
        product2 = createProduct(2L, "Latte", BigDecimal.valueOf(7.00));
        customer = createCustomer();
    }

    // ==================== createOrder ====================

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        // Every create path mints an order number from the shared DailyOrderSequenceService. The two
        // not-found tests throw before reaching it, so this is lenient to stay strict-stubbing clean.
        @BeforeEach
        void stubOrderNumberSource() {
            lenient().when(dailyOrderSequenceService.generateNextOrderNumber())
                    .thenReturn("ORD-20260101-0001");
        }

        @Test
        void createOrder_withoutItems_createsNewOrder() {
            CreateOrderRequest request = createOrderRequest(table.getId());

            when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
                Order o = i.getArgument(0);
                o.setId(1L);
                return o;
            });

            Order result = waiterOrderService.createOrder(request, waiter.getId());

            assertNotNull(result);
            assertEquals(OrderStatus.NEW, result.getStatus());
            assertTrue(result.getItems().isEmpty());
            verify(orderRepository, times(1)).save(any(Order.class));
            verify(orderEventService).recordEvent(any(Order.class), any(), eq(waiter.getName()));
        }

        @Test
        void createOrder_withItems_allItemsPersisted() {
            List<AddOrderItemRequest> items = List.of(
                    createAddItemRequest(product.getId(), 2),
                    createAddItemRequest(product2.getId(), 1)
            );
            CreateOrderRequest request = createOrderRequest(table.getId(), null, items);

            when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2));
            when(inventoryService.checkIngredientAvailability(any(Order.class))).thenReturn(true);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
                Order o = i.getArgument(0);
                o.setId(1L);
                return o;
            });

            Order result = waiterOrderService.createOrder(request, waiter.getId());

            // CRITICAL: verify all items survive -- this was a bug with HashSet deduplication
            assertEquals(2, result.getItems().size());
            verify(inventoryService).deductIngredientsForOrder(any(Order.class));
        }

        @Test
        void createOrder_withMultipleSameProduct_allItemsKept() {
            // Regression test for HashSet bug: two items with same productId but different specialInstructions
            AddOrderItemRequest item1 = createAddItemRequest(product.getId(), 1);
            item1.setSpecialInstructions("No sugar");
            AddOrderItemRequest item2 = createAddItemRequest(product.getId(), 1);
            item2.setSpecialInstructions("Extra sugar");

            CreateOrderRequest request = createOrderRequest(table.getId(), null, List.of(item1, item2));

            when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(inventoryService.checkIngredientAvailability(any(Order.class))).thenReturn(true);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = waiterOrderService.createOrder(request, waiter.getId());

            // Both items should be kept since they have different special instructions
            assertEquals(2, result.getItems().size());
        }

        @Test
        void createOrder_tableNotFound_throws() {
            CreateOrderRequest request = createOrderRequest(999L);

            when(tableRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> waiterOrderService.createOrder(request, waiter.getId()));
        }

        @Test
        void createOrder_waiterNotFound_throws() {
            CreateOrderRequest request = createOrderRequest(table.getId());

            when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
            when(waiterRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> waiterOrderService.createOrder(request, 999L));
        }

        @Test
        void createOrder_withCustomer_setsCustomer() {
            CreateOrderRequest request = createOrderRequest(table.getId(), customer.getId(), List.of());

            when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
                Order o = i.getArgument(0);
                o.setId(1L);
                return o;
            });

            Order result = waiterOrderService.createOrder(request, waiter.getId());

            assertEquals(customer, result.getCustomer());
        }

        @Test
        void createOrder_withVariantPricing_usesVariantPrice() {
            ProductVariant variant = createVariant(10L, "Large", BigDecimal.valueOf(8.00));
            AddOrderItemRequest itemReq = createAddItemRequest(product.getId(), 10L, 2);

            CreateOrderRequest request = createOrderRequest(table.getId(), null, List.of(itemReq));

            when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
            when(inventoryService.checkIngredientAvailability(any(Order.class))).thenReturn(true);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = waiterOrderService.createOrder(request, waiter.getId());

            OrderItem savedItem = result.getItems().get(0);
            assertEquals(0, BigDecimal.valueOf(8.00).compareTo(savedItem.getUnitPrice()));
            assertEquals(0, BigDecimal.valueOf(16.00).compareTo(savedItem.getTotalPrice()));
            assertEquals("Large", savedItem.getVariantName());
        }

        @Test
        void createOrder_insufficientIngredients_throws() {
            List<AddOrderItemRequest> items = List.of(createAddItemRequest(product.getId(), 1));
            CreateOrderRequest request = createOrderRequest(table.getId(), null, items);

            when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(inventoryService.checkIngredientAvailability(any(Order.class))).thenReturn(false);
            when(inventoryService.getMissingIngredients(anyLong(), anyInt())).thenReturn(List.of("Coffee beans"));

            assertThrows(BadRequestException.class,
                    () -> waiterOrderService.createOrder(request, waiter.getId()));
        }

        @Test
        void createOrder_setsTableToOccupied() {
            table.setStatus(TableStatus.AVAILABLE);
            CreateOrderRequest request = createOrderRequest(table.getId());

            when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
                Order o = i.getArgument(0);
                o.setId(1L);
                return o;
            });

            waiterOrderService.createOrder(request, waiter.getId());

            assertEquals(TableStatus.OCCUPIED, table.getStatus());
            verify(tableRepository).save(table);
        }

        @Test
        void createOrder_withItems_autoSubmitsToPreparing() {
            List<AddOrderItemRequest> items = List.of(createAddItemRequest(product.getId(), 1));
            CreateOrderRequest request = createOrderRequest(table.getId(), null, items);

            when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(inventoryService.checkIngredientAvailability(any(Order.class))).thenReturn(true);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = waiterOrderService.createOrder(request, waiter.getId());

            assertEquals(OrderStatus.PREPARING, result.getStatus());
        }
    }

    // ==================== addItems ====================

    @Nested
    @DisplayName("addItems")
    class AddItemsTests {

        @Test
        void addItems_toNewOrder_autoSubmits() {
            Order order = createOrder(1L, OrderStatus.NEW);
            List<AddOrderItemRequest> items = List.of(createAddItemRequest(product.getId(), 1));

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(inventoryService.canMakeProduct(anyLong(), anyInt())).thenReturn(true);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = waiterOrderService.addItems(1L, items, waiter.getId());

            assertEquals(OrderStatus.PREPARING, result.getStatus());
        }

        @Test
        void addItems_toPreparingOrder_keepsStatus() {
            Order order = createOrder(1L, OrderStatus.PREPARING);
            List<AddOrderItemRequest> items = List.of(createAddItemRequest(product.getId(), 1));

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(inventoryService.canMakeProduct(anyLong(), anyInt())).thenReturn(true);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = waiterOrderService.addItems(1L, items, waiter.getId());

            assertEquals(OrderStatus.PREPARING, result.getStatus());
        }

        @Test
        void addItems_toCompletedOrder_throws() {
            Order order = createOrder(1L, OrderStatus.COMPLETED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));

            assertThrows(BadRequestException.class,
                    () -> waiterOrderService.addItems(1L, List.of(createAddItemRequest(product.getId(), 1)), waiter.getId()));
        }

        @Test
        void addItems_toCancelledOrder_throws() {
            Order order = createOrder(1L, OrderStatus.CANCELLED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));

            assertThrows(BadRequestException.class,
                    () -> waiterOrderService.addItems(1L, List.of(createAddItemRequest(product.getId(), 1)), waiter.getId()));
        }

        @Test
        void addItems_multipleItems_allPersisted() {
            Order order = createOrder(1L, OrderStatus.NEW);
            List<AddOrderItemRequest> items = List.of(
                    createAddItemRequest(product.getId(), 2),
                    createAddItemRequest(product2.getId(), 3)
            );

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2));
            when(inventoryService.canMakeProduct(anyLong(), anyInt())).thenReturn(true);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = waiterOrderService.addItems(1L, items, waiter.getId());

            // Regression test: both items must be in the list
            assertEquals(2, result.getItems().size());

            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();
            assertEquals(2, savedOrder.getItems().size());
        }

        @Test
        void addItems_checksInventory() {
            Order order = createOrder(1L, OrderStatus.NEW);
            List<AddOrderItemRequest> items = List.of(createAddItemRequest(product.getId(), 5));

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
            when(inventoryService.canMakeProduct(product.getId(), 5)).thenReturn(false);
            when(inventoryService.getMissingIngredients(product.getId(), 5)).thenReturn(List.of("Milk"));

            assertThrows(BadRequestException.class,
                    () -> waiterOrderService.addItems(1L, items, waiter.getId()));

            verify(inventoryService).canMakeProduct(product.getId(), 5);
        }
    }

    // ==================== updateItem ====================

    @Nested
    @DisplayName("updateItem")
    class UpdateItemTests {

        @Test
        void updateItem_updatesQuantityAndPrice() {
            Order order = createOrder(1L, OrderStatus.PREPARING);
            OrderItem item = createOrderItem(10L, product.getId(), "Espresso", 1, BigDecimal.valueOf(5.00));
            order.getItems().add(item);
            item.setOrder(order);

            UpdateOrderItemRequest updateReq = createUpdateItemRequest(3);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = waiterOrderService.updateItem(1L, 10L, updateReq, waiter.getId());

            OrderItem updatedItem = result.getItems().get(0);
            assertEquals(3, updatedItem.getQuantity());
            assertEquals(0, BigDecimal.valueOf(15.00).compareTo(updatedItem.getTotalPrice()));
        }

        @Test
        void updateItem_itemNotFound_throws() {
            Order order = createOrder(1L, OrderStatus.PREPARING);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));

            assertThrows(ResourceNotFoundException.class,
                    () -> waiterOrderService.updateItem(1L, 999L, createUpdateItemRequest(2), waiter.getId()));
        }

        @Test
        void updateItem_completedOrder_throws() {
            Order order = createOrder(1L, OrderStatus.COMPLETED);
            OrderItem item = createOrderItem(10L, product.getId(), "Espresso", 1, BigDecimal.valueOf(5.00));
            order.getItems().add(item);
            item.setOrder(order);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));

            assertThrows(BadRequestException.class,
                    () -> waiterOrderService.updateItem(1L, 10L, createUpdateItemRequest(2), waiter.getId()));
        }

        @Test
        void updateItem_recalculatesTotals() {
            Order order = createOrder(1L, OrderStatus.PREPARING);
            OrderItem item = createOrderItem(10L, product.getId(), "Espresso", 1, BigDecimal.valueOf(5.00));
            order.getItems().add(item);
            item.setOrder(order);

            UpdateOrderItemRequest updateReq = createUpdateItemRequest(4);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = waiterOrderService.updateItem(1L, 10L, updateReq, waiter.getId());

            // subtotal should be 5.00 * 4 = 20.00
            assertEquals(0, BigDecimal.valueOf(20.00).compareTo(result.getSubtotal()));
            assertEquals(0, BigDecimal.valueOf(20.00).compareTo(result.getTotal()));
        }
    }

    // ==================== removeItem ====================

    @Nested
    @DisplayName("removeItem")
    class RemoveItemTests {

        @Test
        void removeItem_success() {
            Order order = createOrder(1L, OrderStatus.PREPARING);
            OrderItem item = createOrderItem(10L, product.getId(), "Espresso", 1, BigDecimal.valueOf(5.00));
            order.getItems().add(item);
            item.setOrder(order);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = waiterOrderService.removeItem(1L, 10L, waiter.getId());

            assertTrue(result.getItems().isEmpty());
        }

        @Test
        void removeItem_itemNotFound_throws() {
            Order order = createOrder(1L, OrderStatus.PREPARING);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));

            assertThrows(ResourceNotFoundException.class,
                    () -> waiterOrderService.removeItem(1L, 999L, waiter.getId()));
        }

        @Test
        void removeItem_completedOrder_throws() {
            Order order = createOrder(1L, OrderStatus.COMPLETED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));

            assertThrows(BadRequestException.class,
                    () -> waiterOrderService.removeItem(1L, 10L, waiter.getId()));
        }

        @Test
        void removeItem_recalculatesTotals() {
            Order order = createOrder(1L, OrderStatus.PREPARING);
            OrderItem item1 = createOrderItem(10L, product.getId(), "Espresso", 2, BigDecimal.valueOf(5.00));
            OrderItem item2 = createOrderItem(11L, product2.getId(), "Latte", 1, BigDecimal.valueOf(7.00));
            order.getItems().add(item1);
            order.getItems().add(item2);
            item1.setOrder(order);
            item2.setOrder(order);
            // Set initial totals
            order.setSubtotal(BigDecimal.valueOf(17.00));
            order.setTotal(BigDecimal.valueOf(17.00));

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = waiterOrderService.removeItem(1L, 10L, waiter.getId());

            // Only item2 remains: 7.00
            assertEquals(1, result.getItems().size());
            assertEquals(0, BigDecimal.valueOf(7.00).compareTo(result.getSubtotal()));
            assertEquals(0, BigDecimal.valueOf(7.00).compareTo(result.getTotal()));
        }
    }

    // ==================== submitToKitchen ====================

    @Nested
    @DisplayName("submitToKitchen")
    class SubmitToKitchenTests {

        @Test
        void submitToKitchen_success_setsStatusPreparing() {
            Order order = createOrder(1L, OrderStatus.NEW);
            OrderItem item = createOrderItem(10L, product.getId(), "Espresso", 1, BigDecimal.valueOf(5.00));
            order.getItems().add(item);
            item.setOrder(order);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(inventoryService.checkIngredientAvailability(any(Order.class))).thenReturn(true);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = waiterOrderService.submitToKitchen(1L, waiter.getId());

            assertEquals(OrderStatus.PREPARING, result.getStatus());
            verify(inventoryService).deductIngredientsForOrder(any(Order.class));
        }

        @Test
        void submitToKitchen_emptyItems_throws() {
            Order order = createOrder(1L, OrderStatus.NEW);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));

            assertThrows(BadRequestException.class,
                    () -> waiterOrderService.submitToKitchen(1L, waiter.getId()));
        }

        @Test
        void submitToKitchen_alreadySubmitted_throws() {
            Order order = createOrder(1L, OrderStatus.PREPARING);
            OrderItem item = createOrderItem(10L, product.getId(), "Espresso", 1, BigDecimal.valueOf(5.00));
            order.getItems().add(item);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));

            assertThrows(BadRequestException.class,
                    () -> waiterOrderService.submitToKitchen(1L, waiter.getId()));
        }

        @Test
        void submitToKitchen_insufficientIngredients_throws() {
            Order order = createOrder(1L, OrderStatus.NEW);
            OrderItem item = createOrderItem(10L, product.getId(), "Espresso", 1, BigDecimal.valueOf(5.00));
            order.getItems().add(item);
            item.setOrder(order);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(inventoryService.checkIngredientAvailability(any(Order.class))).thenReturn(false);
            when(inventoryService.getMissingIngredients(anyLong(), anyInt())).thenReturn(List.of("Coffee beans"));

            assertThrows(BadRequestException.class,
                    () -> waiterOrderService.submitToKitchen(1L, waiter.getId()));

            verify(inventoryService, never()).deductIngredientsForOrder(any(Order.class));
        }
    }

    // ==================== closeOrder ====================

    @Nested
    @DisplayName("closeOrder")
    class CloseOrderTests {

        private void makeOrderPaid(Order order) {
            order.setTotal(BigDecimal.valueOf(100));
            order.setGrandTotal(BigDecimal.valueOf(100));
            order.setPayments(new java.util.ArrayList<>());
            com.elcafe.modules.order.entity.Payment payment = com.elcafe.modules.order.entity.Payment.builder()
                    .id(1L).amount(BigDecimal.valueOf(100)).tipAmount(BigDecimal.ZERO)
                    .refundedAmount(BigDecimal.ZERO).status(com.elcafe.modules.order.enums.PaymentStatus.COMPLETED).build();
            order.addPayment(payment);
        }

        @Test
        void closeOrder_alreadyCompleted_isIdempotentNoOp() {
            Order order = createOrder(1L, OrderStatus.COMPLETED);
            makeOrderPaid(order);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));

            Order result = waiterOrderService.closeOrder(1L, waiter.getId());

            // Re-closing must not replay side effects: no save, no ORDER_CLOSED event, no OrderPaid
            // broadcast, no commission recalculation, no completion-event gate call.
            assertEquals(OrderStatus.COMPLETED, result.getStatus());
            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(orderEventService, orderEventPublisher,
                    waiterCommissionService, orderCompletionEvents);
        }

        @Test
        void closeOrder_success_setsCompleted() {
            Order order = createOrder(1L, OrderStatus.PREPARING);
            makeOrderPaid(order);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(waiterCommissionService.calculateCommissionForOrder(any(Order.class))).thenReturn(Optional.empty());

            Order result = waiterOrderService.closeOrder(1L, waiter.getId());

            assertEquals(OrderStatus.COMPLETED, result.getStatus());
            // Waiter close is a settling moment: the completion gate must be consulted.
            verify(orderCompletionEvents).publishIfQualified(result);
        }

        @Test
        void closeOrder_setsTableToCleaning() {
            Order order = createOrder(1L, OrderStatus.PREPARING);
            makeOrderPaid(order);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(waiterCommissionService.calculateCommissionForOrder(any(Order.class))).thenReturn(Optional.empty());

            waiterOrderService.closeOrder(1L, waiter.getId());

            assertEquals(TableStatus.CLEANING, order.getDiningTable().getStatus());
            verify(tableRepository).save(order.getDiningTable());
        }

        @Test
        void closeOrder_calculatesCommission() {
            Order order = createOrder(1L, OrderStatus.PREPARING);
            makeOrderPaid(order);

            WaiterCommission commission = WaiterCommission.builder()
                    .commissionAmount(BigDecimal.valueOf(2.50))
                    .commissionPercent(BigDecimal.valueOf(5))
                    .build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(waiterRepository.findById(waiter.getId())).thenReturn(Optional.of(waiter));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(waiterCommissionService.calculateCommissionForOrder(any(Order.class))).thenReturn(Optional.of(commission));

            waiterOrderService.closeOrder(1L, waiter.getId());

            verify(waiterCommissionService).calculateCommissionForOrder(any(Order.class));
        }
    }

    // ==================== getTableOrders ====================

    @Nested
    @DisplayName("getTableOrders")
    class GetTableOrdersTests {

        @Test
        void getTableOrders_filtersCompletedAndCancelled() {
            Order newOrder = createOrder(1L, OrderStatus.NEW);
            Order preparingOrder = createOrder(2L, OrderStatus.PREPARING);
            Order completedOrder = createOrder(3L, OrderStatus.COMPLETED);
            Order cancelledOrder = createOrder(4L, OrderStatus.CANCELLED);

            List<Order> allOrders = new ArrayList<>(List.of(newOrder, preparingOrder, completedOrder, cancelledOrder));
            table.setOrders(allOrders);

            when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));

            List<Order> result = waiterOrderService.getTableOrders(table.getId());

            assertEquals(2, result.size());
            assertTrue(result.stream().noneMatch(o -> o.getStatus() == OrderStatus.COMPLETED));
            assertTrue(result.stream().noneMatch(o -> o.getStatus() == OrderStatus.CANCELLED));
            assertTrue(result.stream().anyMatch(o -> o.getStatus() == OrderStatus.NEW));
            assertTrue(result.stream().anyMatch(o -> o.getStatus() == OrderStatus.PREPARING));
        }
    }
}
