package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;

import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.dto.pos.ModifyOrderItemRequest;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrderItem;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createProduct;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class POSOrderItemServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private POSOrderItemService posOrderItemService;

    private Order order;
    private Product product;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.PREPARING);
        order.setDiscount(BigDecimal.ZERO);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setServiceFee(BigDecimal.ZERO);
        order.setServiceFeePercent(BigDecimal.ZERO);
        order.setEntryFee(BigDecimal.ZERO);
        order.setTipAmount(BigDecimal.ZERO);

        product = createProduct(1L, "Latte", BigDecimal.valueOf(8000));
    }

    private ModifyOrderItemRequest buildRequest(Long productId, int qty) {
        ModifyOrderItemRequest r = new ModifyOrderItemRequest();
        r.setProductId(productId);
        r.setQuantity(qty);
        return r;
    }

    // ==================== addItemToOrder ====================

    @Nested
    @DisplayName("addItemToOrder")
    class AddItemToOrder {

        @Test
        @DisplayName("1. success - adds item and recalculates totals")
        void addItem_success_addsItemAndRecalculates() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(inventoryService.getMissingIngredients(anyLong(), anyInt())).thenReturn(List.of());
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = posOrderItemService.addItemToOrder(1L, buildRequest(1L, 2));

            assertEquals(1, result.getItems().size());
            OrderItem added = result.getItems().get(0);
            assertEquals("Latte", added.getProductName());
            assertEquals(2, added.getQuantity());
            assertEquals(0, BigDecimal.valueOf(8000).compareTo(added.getUnitPrice()));
            assertEquals(0, BigDecimal.valueOf(16000).compareTo(added.getTotalPrice()));
            assertEquals(0, BigDecimal.valueOf(16000).compareTo(result.getSubtotal()));
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("2. order not found - throws a typed exception")
        void addItem_orderNotFound_throws() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> posOrderItemService.addItemToOrder(99L, buildRequest(1L, 1)));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("3. product not found - throws a typed exception")
        void addItem_productNotFound_throws() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> posOrderItemService.addItemToOrder(1L, buildRequest(99L, 1)));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("4. completed order - throws a typed exception")
        void addItem_completedOrder_throws() {
            order.setStatus(OrderStatus.COMPLETED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThrows(BadRequestException.class,
                    () -> posOrderItemService.addItemToOrder(1L, buildRequest(1L, 1)));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("5. delivered order - throws a typed exception")
        void addItem_deliveredOrder_throws() {
            order.setStatus(OrderStatus.DELIVERED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThrows(BadRequestException.class,
                    () -> posOrderItemService.addItemToOrder(1L, buildRequest(1L, 1)));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("6. READY order - succeeds (READY is allowed)")
        void addItem_readyOrder_succeeds() {
            order.setStatus(OrderStatus.READY);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(inventoryService.getMissingIngredients(anyLong(), anyInt())).thenReturn(List.of());
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = posOrderItemService.addItemToOrder(1L, buildRequest(1L, 1));

            assertEquals(1, result.getItems().size());
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("7. insufficient inventory - throws a typed exception")
        void addItem_insufficientInventory_throws() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(inventoryService.getMissingIngredients(anyLong(), anyInt()))
                    .thenReturn(List.of("Sugar (need: 500 g, have: 100 g)"));

            assertThrows(BadRequestException.class,
                    () -> posOrderItemService.addItemToOrder(1L, buildRequest(1L, 1)));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("8. with modifiers - adds modifiers and includes modifier price in total")
        void addItem_withModifiers_addsModifiers() {
            ModifyOrderItemRequest.ModifierInfo mod1 = ModifyOrderItemRequest.ModifierInfo.builder()
                    .addOnId(100L)
                    .name("Extra Shot")
                    .price(BigDecimal.valueOf(1500))
                    .quantity(1)
                    .build();
            ModifyOrderItemRequest.ModifierInfo mod2 = ModifyOrderItemRequest.ModifierInfo.builder()
                    .addOnId(101L)
                    .name("Oat Milk")
                    .price(BigDecimal.valueOf(750))
                    .quantity(1)
                    .build();

            ModifyOrderItemRequest request = ModifyOrderItemRequest.builder()
                    .productId(1L)
                    .quantity(2)
                    .modifiers(List.of(mod1, mod2))
                    .build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(inventoryService.getMissingIngredients(anyLong(), anyInt())).thenReturn(List.of());
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = posOrderItemService.addItemToOrder(1L, request);

            assertEquals(1, result.getItems().size());
            OrderItem added = result.getItems().get(0);

            // base = 8000 * 2 = 16000, modifiers = (1500 + 750) * 2 = 4500, total = 20500
            assertEquals(0, BigDecimal.valueOf(20500).compareTo(added.getTotalPrice()));
            assertEquals(2, added.getItemAddOns().size());
            assertTrue(added.getAddOns().contains("Extra Shot"));
            assertTrue(added.getAddOns().contains("Oat Milk"));
        }
    }

    // ==================== removeItemFromOrder ====================

    @Nested
    @DisplayName("removeItemFromOrder")
    class RemoveItemFromOrder {

        @Test
        @DisplayName("9. success - removes item and recalculates")
        void removeItem_success_removesAndRecalculates() {
            OrderItem item = createOrderItem(10L, 1L, "Latte", 1, BigDecimal.valueOf(8000));
            order.getItems().add(item);
            item.setOrder(order);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = posOrderItemService.removeItemFromOrder(1L, 10L);

            assertEquals(0, result.getItems().size());
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getSubtotal()));
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("10. order not found - throws a typed exception")
        void removeItem_orderNotFound_throws() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> posOrderItemService.removeItemFromOrder(99L, 10L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("11. item not found - throws a typed exception")
        void removeItem_itemNotFound_throws() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThrows(ResourceNotFoundException.class,
                    () -> posOrderItemService.removeItemFromOrder(1L, 999L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("12. completed order - throws a typed exception")
        void removeItem_completedOrder_throws() {
            order.setStatus(OrderStatus.COMPLETED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThrows(BadRequestException.class,
                    () -> posOrderItemService.removeItemFromOrder(1L, 10L));
            verify(orderRepository, never()).save(any());
        }
    }

    // ==================== updateItemQuantity ====================

    @Nested
    @DisplayName("updateItemQuantity")
    class UpdateItemQuantity {

        @Test
        @DisplayName("13. success - updates quantity and recalculates")
        void updateQuantity_success_updatesAndRecalculates() {
            OrderItem item = createOrderItem(10L, 1L, "Latte", 1, BigDecimal.valueOf(8000));
            order.getItems().add(item);
            item.setOrder(order);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = posOrderItemService.updateItemQuantity(1L, 10L, 3);

            OrderItem updated = result.getItems().get(0);
            assertEquals(3, updated.getQuantity());
            assertEquals(0, BigDecimal.valueOf(24000).compareTo(updated.getTotalPrice()));
            assertEquals(0, BigDecimal.valueOf(24000).compareTo(result.getSubtotal()));
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("14. quantity zero - delegates to removeItemFromOrder")
        void updateQuantity_toZero_removesItem() {
            OrderItem item = createOrderItem(10L, 1L, "Latte", 1, BigDecimal.valueOf(8000));
            order.getItems().add(item);
            item.setOrder(order);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Order result = posOrderItemService.updateItemQuantity(1L, 10L, 0);

            assertEquals(0, result.getItems().size());
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getSubtotal()));
        }

        @Test
        @DisplayName("15. item not found - throws a typed exception")
        void updateQuantity_itemNotFound_throws() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThrows(ResourceNotFoundException.class,
                    () -> posOrderItemService.updateItemQuantity(1L, 999L, 3));
            verify(orderRepository, never()).save(any());
        }
    }

    // ==================== recalculateOrderTotals ====================

    @Nested
    @DisplayName("recalculateOrderTotals")
    class RecalculateOrderTotals {

        @Test
        @DisplayName("16. correct subtotal from items")
        void recalculate_correctSubtotalFromItems() {
            order.getItems().add(createOrderItem(1L, 1L, "Latte", 2, BigDecimal.valueOf(8000)));
            order.getItems().add(createOrderItem(2L, 2L, "Cookie", 1, BigDecimal.valueOf(3000)));

            posOrderItemService.recalculateOrderTotals(order);

            // 2*8000 + 1*3000 = 19000
            assertEquals(0, BigDecimal.valueOf(19000).compareTo(order.getSubtotal()));
            assertEquals(0, BigDecimal.ZERO.compareTo(order.getTax()));
            assertEquals(0, BigDecimal.valueOf(19000).compareTo(order.getTotal()));
        }

        @Test
        @DisplayName("17. includes service fee based on percent")
        void recalculate_includesServiceFee() {
            order.getItems().add(createOrderItem(1L, 1L, "Latte", 1, BigDecimal.valueOf(100000)));
            order.setServiceFeePercent(BigDecimal.valueOf(10)); // 10%

            posOrderItemService.recalculateOrderTotals(order);

            assertEquals(0, BigDecimal.valueOf(10000).setScale(2).compareTo(order.getServiceFee()));
            // total = subtotal + serviceFee = 100000 + 10000 = 110000
            assertEquals(0, BigDecimal.valueOf(110000).compareTo(order.getTotal()));
        }

        @Test
        @DisplayName("18. subtracts discount from total")
        void recalculate_subtractsDiscount() {
            order.getItems().add(createOrderItem(1L, 1L, "Latte", 1, BigDecimal.valueOf(10000)));
            order.setDiscount(BigDecimal.valueOf(2000));

            posOrderItemService.recalculateOrderTotals(order);

            assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getSubtotal()));
            assertEquals(0, BigDecimal.valueOf(8000).compareTo(order.getTotal()));
        }

        @Test
        @DisplayName("19. grand total syncs with tip")
        void recalculate_syncsGrandTotalWithTip() {
            order.getItems().add(createOrderItem(1L, 1L, "Latte", 1, BigDecimal.valueOf(10000)));
            order.setTipAmount(BigDecimal.valueOf(2000));

            posOrderItemService.recalculateOrderTotals(order);

            assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getTotal()));
            assertEquals(0, BigDecimal.valueOf(12000).compareTo(order.getGrandTotal()));
        }

        @Test
        @DisplayName("20. null-safe on all fees - treats null fees as zero")
        void recalculate_nullSafeOnAllFees() {
            order.getItems().add(createOrderItem(1L, 1L, "Latte", 1, BigDecimal.valueOf(10000)));
            order.setDiscount(null);
            order.setDeliveryFee(null);
            order.setServiceFee(null);
            order.setServiceFeePercent(null);
            order.setEntryFee(null);
            order.setTipAmount(null);

            assertDoesNotThrow(() -> posOrderItemService.recalculateOrderTotals(order));
            assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getSubtotal()));
            assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getTotal()));
            assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getGrandTotal()));
        }
    }
}
