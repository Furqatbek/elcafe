package com.elcafe.modules.order.service;

import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.dto.pos.ModifyOrderItemRequest;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
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
class POSOrderItemServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryService inventoryService;

    @InjectMocks private POSOrderItemService posOrderItemService;

    private Order order;
    private Product product;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.PREPARING);
        order.setDiscount(BigDecimal.ZERO);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setServiceFee(BigDecimal.ZERO);
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

    @Test
    void addItem_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryService.getMissingIngredients(anyLong(), anyInt())).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = posOrderItemService.addItemToOrder(1L, buildRequest(1L, 2));

        assertEquals(1, result.getItems().size());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void addItem_orderNotFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> posOrderItemService.addItemToOrder(99L, buildRequest(1L, 1)));
    }

    @Test
    void addItem_productNotFound_throws() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> posOrderItemService.addItemToOrder(1L, buildRequest(99L, 1)));
    }

    @Test
    void addItem_completedOrder_throws() {
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        assertThrows(IllegalStateException.class,
                () -> posOrderItemService.addItemToOrder(1L, buildRequest(1L, 1)));
    }

    @Test
    void addItem_deliveredOrder_throws() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        assertThrows(IllegalStateException.class,
                () -> posOrderItemService.addItemToOrder(1L, buildRequest(1L, 1)));
    }

    @Test
    void addItem_readyOrder_succeeds() {
        order.setStatus(OrderStatus.READY);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryService.getMissingIngredients(anyLong(), anyInt())).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = posOrderItemService.addItemToOrder(1L, buildRequest(1L, 1));
        assertEquals(1, result.getItems().size());
    }

    @Test
    void addItem_insufficientInventory_throws() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryService.getMissingIngredients(anyLong(), anyInt()))
                .thenReturn(List.of("Missing: Sugar"));

        assertThrows(IllegalStateException.class,
                () -> posOrderItemService.addItemToOrder(1L, buildRequest(1L, 1)));
    }

    // ==================== removeItemFromOrder ====================

    @Test
    void removeItem_success() {
        OrderItem item = createOrderItem(10L, 1L, "Latte", 1, BigDecimal.valueOf(8000));
        order.getItems().add(item);
        item.setOrder(order);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = posOrderItemService.removeItemFromOrder(1L, 10L);
        assertEquals(0, result.getItems().size());
    }

    @Test
    void removeItem_itemNotFound_throws() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        assertThrows(IllegalArgumentException.class,
                () -> posOrderItemService.removeItemFromOrder(1L, 999L));
    }

    @Test
    void removeItem_completedOrder_throws() {
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        assertThrows(IllegalStateException.class,
                () -> posOrderItemService.removeItemFromOrder(1L, 10L));
    }

    // ==================== updateItemQuantity ====================

    @Test
    void updateQuantity_success() {
        OrderItem item = createOrderItem(10L, 1L, "Latte", 1, BigDecimal.valueOf(8000));
        order.getItems().add(item);
        item.setOrder(order);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = posOrderItemService.updateItemQuantity(1L, 10L, 3);

        OrderItem updated = result.getItems().get(0);
        assertEquals(3, updated.getQuantity());
        assertEquals(0, BigDecimal.valueOf(24000).compareTo(updated.getTotalPrice()));
    }

    @Test
    void updateQuantity_toZero_removesItem() {
        OrderItem item = createOrderItem(10L, 1L, "Latte", 1, BigDecimal.valueOf(8000));
        order.getItems().add(item);
        item.setOrder(order);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = posOrderItemService.updateItemQuantity(1L, 10L, 0);
        assertEquals(0, result.getItems().size());
    }

    @Test
    void updateQuantity_itemNotFound_throws() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        assertThrows(IllegalArgumentException.class,
                () -> posOrderItemService.updateItemQuantity(1L, 999L, 3));
    }

    // ==================== recalculateOrderTotals ====================

    @Test
    void recalculate_correctSubtotal() {
        order.getItems().add(createOrderItem(1L, 1L, "Latte", 2, BigDecimal.valueOf(8000)));
        order.getItems().add(createOrderItem(2L, 2L, "Cookie", 1, BigDecimal.valueOf(3000)));

        posOrderItemService.recalculateOrderTotals(order);

        // 2*8000 + 1*3000 = 19000
        assertEquals(0, BigDecimal.valueOf(19000).compareTo(order.getSubtotal()));
        assertEquals(0, BigDecimal.valueOf(19000).compareTo(order.getTotal()));
    }

    @Test
    void recalculate_subtractsDiscount() {
        order.getItems().add(createOrderItem(1L, 1L, "Latte", 1, BigDecimal.valueOf(10000)));
        order.setDiscount(BigDecimal.valueOf(2000));

        posOrderItemService.recalculateOrderTotals(order);

        assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getSubtotal()));
        assertEquals(0, BigDecimal.valueOf(8000).compareTo(order.getTotal()));
    }

    @Test
    void recalculate_includesServiceFee() {
        order.getItems().add(createOrderItem(1L, 1L, "Latte", 1, BigDecimal.valueOf(100000)));
        order.setServiceFeePercent(BigDecimal.valueOf(10)); // 10%

        posOrderItemService.recalculateOrderTotals(order);

        assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getServiceFee()));
        assertEquals(0, BigDecimal.valueOf(110000).compareTo(order.getTotal()));
    }

    @Test
    void recalculate_syncsGrandTotalWithTip() {
        order.getItems().add(createOrderItem(1L, 1L, "Latte", 1, BigDecimal.valueOf(10000)));
        order.setTipAmount(BigDecimal.valueOf(2000));

        posOrderItemService.recalculateOrderTotals(order);

        assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getTotal()));
        assertEquals(0, BigDecimal.valueOf(12000).compareTo(order.getGrandTotal()));
    }

    @Test
    void recalculate_nullSafeOnAllFees() {
        order.getItems().add(createOrderItem(1L, 1L, "Latte", 1, BigDecimal.valueOf(10000)));
        order.setDiscount(null);
        order.setDeliveryFee(null);
        order.setServiceFee(null);
        order.setEntryFee(null);
        order.setTipAmount(null);

        assertDoesNotThrow(() -> posOrderItemService.recalculateOrderTotals(order));
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getTotal()));
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getGrandTotal()));
    }

    // ==================== canModifyOrder ====================

    @Test
    void canModifyOrder_allowedStatuses() {
        for (OrderStatus status : List.of(OrderStatus.NEW, OrderStatus.PENDING,
                OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)) {
            order.setStatus(status);
            assertTrue(posOrderItemService.canModifyOrder(order), "Should allow " + status);
        }
    }

    @Test
    void canModifyOrder_blockedStatuses() {
        for (OrderStatus status : List.of(OrderStatus.COMPLETED, OrderStatus.DELIVERED, OrderStatus.CANCELLED)) {
            order.setStatus(status);
            assertFalse(posOrderItemService.canModifyOrder(order), "Should block " + status);
        }
    }
}
