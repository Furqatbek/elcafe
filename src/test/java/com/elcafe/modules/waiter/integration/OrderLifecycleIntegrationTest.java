package com.elcafe.modules.waiter.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.entity.RestaurantTable.TableStatus;
import com.elcafe.modules.waiter.entity.OrderEvent;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterCommission;
import com.elcafe.modules.waiter.enums.OrderEventType;
import com.elcafe.modules.waiter.enums.WaiterRole;
import com.elcafe.modules.waiter.repository.OrderEventRepository;
import com.elcafe.modules.waiter.repository.WaiterCommissionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the full order lifecycle.
 * Uses real H2 database to verify persistence at every stage.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class OrderLifecycleIntegrationTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderEventRepository orderEventRepository;
    @Autowired private WaiterCommissionRepository waiterCommissionRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private RestaurantTable table;
    private Waiter waiter;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setCity("Tashkent");
        restaurant.setPhone("+998901234567");
        restaurant.setEmail("test@restaurant.com");
        restaurant.setActive(true);
        restaurant.setAcceptingOrders(true);
        restaurant.setDeliveryFee(BigDecimal.ZERO);
        em.persist(restaurant);

        table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber("T1");
        table.setTableName("Table 1");
        table.setStatus(TableStatus.AVAILABLE);
        table.setCapacity(4);
        table.setActive(true);
        em.persist(table);

        waiter = new Waiter();
        waiter.setName("Test Waiter");
        waiter.setPinCode("1234");
        waiter.setRole(WaiterRole.WAITER);
        waiter.setActive(true);
        waiter.setCommissionEnabled(true);
        waiter.setCommissionPercent(BigDecimal.valueOf(5));
        em.persist(waiter);

        em.flush();
    }

    private Order createBaseOrder(String orderNumber) {
        return Order.builder()
                .orderNumber(orderNumber)
                .restaurant(restaurant)
                .diningTable(table)
                .waiter(waiter)
                .status(OrderStatus.NEW)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .subtotal(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();
    }

    private OrderItem createItem(Long productId, String name, int qty, BigDecimal price) {
        return OrderItem.builder()
                .productId(productId)
                .productName(name)
                .quantity(qty)
                .unitPrice(price)
                .totalPrice(price.multiply(BigDecimal.valueOf(qty)))
                .build();
    }

    // ==================== Full lifecycle ====================

    @Test
    @DisplayName("Full order lifecycle: NEW → PREPARING → READY → COMPLETED")
    void fullOrderLifecycle_createToComplete() {
        // Create NEW order with items
        Order order = createBaseOrder("LIFE-001");
        order.addItem(createItem(1L, "Latte", 2, BigDecimal.valueOf(8.00)));
        order.addItem(createItem(2L, "Cookie", 1, BigDecimal.valueOf(3.00)));
        order.setSubtotal(BigDecimal.valueOf(19.00));
        order.setTotal(BigDecimal.valueOf(19.00));

        Order saved = orderRepository.save(order);
        em.flush();
        em.clear();

        // Verify NEW
        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(OrderStatus.NEW, reloaded.getStatus());
        assertEquals(2, reloaded.getItems().size());

        // → PREPARING
        reloaded.setStatus(OrderStatus.PREPARING);
        orderRepository.save(reloaded);
        em.flush();
        em.clear();

        reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(OrderStatus.PREPARING, reloaded.getStatus());
        assertEquals(2, reloaded.getItems().size()); // items still there

        // → READY
        reloaded.setStatus(OrderStatus.READY);
        orderRepository.save(reloaded);
        em.flush();
        em.clear();

        reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(OrderStatus.READY, reloaded.getStatus());

        // → COMPLETED
        reloaded.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(reloaded);
        em.flush();
        em.clear();

        reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, reloaded.getStatus());
        assertEquals(2, reloaded.getItems().size()); // items survive full lifecycle
    }

    // ==================== Add items to existing order ====================

    @Test
    @DisplayName("Create with 2 items, add 3 more, verify all 5 persisted")
    void createOrder_thenAddMoreItems_allPersisted() {
        Order order = createBaseOrder("ADD-001");
        order.addItem(createItem(1L, "Espresso", 1, BigDecimal.valueOf(5.00)));
        order.addItem(createItem(2L, "Cappuccino", 1, BigDecimal.valueOf(7.00)));

        Order saved = orderRepository.save(order);
        em.flush();
        em.clear();

        // Add 3 more
        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(2, reloaded.getItems().size());

        reloaded.addItem(createItem(3L, "Latte", 1, BigDecimal.valueOf(8.00)));
        reloaded.addItem(createItem(4L, "Mocha", 1, BigDecimal.valueOf(9.00)));
        reloaded.addItem(createItem(5L, "Cookie", 2, BigDecimal.valueOf(3.00)));
        orderRepository.save(reloaded);
        em.flush();
        em.clear();

        Order finalOrder = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(5, finalOrder.getItems().size(), "All 5 items must be persisted");
    }

    // ==================== Update item quantity ====================

    @Test
    @DisplayName("Updating item quantity persists the change")
    void updateItemQuantity_persistsChange() {
        Order order = createBaseOrder("UPD-001");
        order.addItem(createItem(1L, "Latte", 1, BigDecimal.valueOf(8.00)));

        Order saved = orderRepository.save(order);
        em.flush();
        em.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        OrderItem item = reloaded.getItems().get(0);
        item.setQuantity(3);
        item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(3)));
        orderRepository.save(reloaded);
        em.flush();
        em.clear();

        Order finalOrder = orderRepository.findById(saved.getId()).orElseThrow();
        OrderItem updatedItem = finalOrder.getItems().get(0);
        assertEquals(3, updatedItem.getQuantity());
        assertEquals(0, BigDecimal.valueOf(24.00).compareTo(updatedItem.getTotalPrice()));
    }

    // ==================== Remove item ====================

    @Test
    @DisplayName("Removing item triggers orphan removal in database")
    void removeItem_orphanRemovalWorks() {
        Order order = createBaseOrder("REM-001");
        order.addItem(createItem(1L, "Latte", 1, BigDecimal.valueOf(8.00)));
        order.addItem(createItem(2L, "Cookie", 1, BigDecimal.valueOf(3.00)));
        order.addItem(createItem(3L, "Cake", 1, BigDecimal.valueOf(12.00)));

        Order saved = orderRepository.save(order);
        em.flush();
        em.clear();

        // Remove middle item (Cookie)
        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        OrderItem toRemove = reloaded.getItems().stream()
                .filter(i -> "Cookie".equals(i.getProductName()))
                .findFirst().orElseThrow();
        reloaded.getItems().remove(toRemove);
        orderRepository.save(reloaded);
        em.flush();
        em.clear();

        Order finalOrder = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(2, finalOrder.getItems().size());
        List<String> names = finalOrder.getItems().stream()
                .map(OrderItem::getProductName).sorted().toList();
        assertEquals(List.of("Cake", "Latte"), names);
    }

    @Test
    @DisplayName("Removing all items leaves order with 0 items")
    void removeAllItems_orderStillExists() {
        Order order = createBaseOrder("REM-002");
        order.addItem(createItem(1L, "Latte", 1, BigDecimal.valueOf(8.00)));
        order.addItem(createItem(2L, "Cookie", 1, BigDecimal.valueOf(3.00)));

        Order saved = orderRepository.save(order);
        em.flush();
        em.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        reloaded.getItems().clear();
        orderRepository.save(reloaded);
        em.flush();
        em.clear();

        Order finalOrder = orderRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(finalOrder);
        assertEquals(0, finalOrder.getItems().size());
    }

    // ==================== Multiple orders ====================

    @Test
    @DisplayName("Multiple orders for same table all persist")
    void multipleOrdersForSameTable_allPersisted() {
        for (int i = 1; i <= 3; i++) {
            Order order = createBaseOrder("MULTI-00" + i);
            order.addItem(createItem((long) i, "Product " + i, 1, BigDecimal.valueOf(10.00)));
            orderRepository.save(order);
        }
        em.flush();
        em.clear();

        assertEquals(3, orderRepository.count());
    }

    // ==================== Commission ====================

    @Test
    @DisplayName("Commission persists linked to waiter and order")
    void orderWithCommission_persistsCommission() {
        Order order = createBaseOrder("COM-001");
        order.addItem(createItem(1L, "Expensive Dish", 1, BigDecimal.valueOf(100.00)));
        order.setSubtotal(BigDecimal.valueOf(100.00));
        order.setTotal(BigDecimal.valueOf(100.00));
        order.setStatus(OrderStatus.COMPLETED);
        Order saved = orderRepository.save(order);
        em.flush();

        WaiterCommission commission = WaiterCommission.builder()
                .waiter(waiter)
                .order(saved)
                .restaurant(restaurant)
                .orderTotal(BigDecimal.valueOf(100.00))
                .commissionPercent(BigDecimal.valueOf(5))
                .commissionAmount(BigDecimal.valueOf(5.00))
                .build();
        waiterCommissionRepository.save(commission);
        em.flush();
        em.clear();

        List<WaiterCommission> commissions = waiterCommissionRepository.findAll();
        assertEquals(1, commissions.size());
        WaiterCommission reloaded = commissions.get(0);
        assertEquals(0, BigDecimal.valueOf(5.00).compareTo(reloaded.getCommissionAmount()));
        assertEquals(0, BigDecimal.valueOf(5).compareTo(reloaded.getCommissionPercent()));
        assertEquals(saved.getId(), reloaded.getOrder().getId());
        assertEquals(waiter.getId(), reloaded.getWaiter().getId());
    }

    // ==================== Order Events ====================

    @Test
    @DisplayName("Order events persist history")
    void orderEvents_persistHistory() {
        Order order = createBaseOrder("EVT-001");
        order.addItem(createItem(1L, "Latte", 1, BigDecimal.valueOf(8.00)));
        Order saved = orderRepository.save(order);
        em.flush();

        // Record 3 events
        for (OrderEventType type : List.of(
                OrderEventType.ORDER_CREATED,
                OrderEventType.ORDER_UPDATED,
                OrderEventType.ORDER_CLOSED)) {
            OrderEvent event = OrderEvent.builder()
                    .order(saved)
                    .eventType(type)
                    .triggeredBy("Test Waiter")
                    .build();
            orderEventRepository.save(event);
        }
        em.flush();
        em.clear();

        List<OrderEvent> events = orderEventRepository.findByOrderIdOrderByCreatedAtAsc(saved.getId());
        assertEquals(3, events.size());
        assertEquals(OrderEventType.ORDER_CREATED, events.get(0).getEventType());
        assertEquals(OrderEventType.ORDER_UPDATED, events.get(1).getEventType());
        assertEquals(OrderEventType.ORDER_CLOSED, events.get(2).getEventType());
    }

    // ==================== Concurrent modifications ====================

    @Test
    @DisplayName("Add item and update existing item in same transaction both persist")
    void concurrentModifications_allPersisted() {
        Order order = createBaseOrder("CONC-001");
        order.addItem(createItem(1L, "Latte", 1, BigDecimal.valueOf(8.00)));
        Order saved = orderRepository.save(order);
        em.flush();
        em.clear();

        // Reload, add new item AND update existing
        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(1, reloaded.getItems().size());

        // Update existing item quantity
        OrderItem existing = reloaded.getItems().get(0);
        existing.setQuantity(3);
        existing.setTotalPrice(existing.getUnitPrice().multiply(BigDecimal.valueOf(3)));

        // Add new item
        reloaded.addItem(createItem(2L, "Cookie", 2, BigDecimal.valueOf(3.00)));

        orderRepository.save(reloaded);
        em.flush();
        em.clear();

        Order finalOrder = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(2, finalOrder.getItems().size(), "Both items present");

        OrderItem latte = finalOrder.getItems().stream()
                .filter(i -> "Latte".equals(i.getProductName()))
                .findFirst().orElseThrow();
        assertEquals(3, latte.getQuantity(), "Quantity update persisted");

        OrderItem cookie = finalOrder.getItems().stream()
                .filter(i -> "Cookie".equals(i.getProductName()))
                .findFirst().orElseThrow();
        assertEquals(2, cookie.getQuantity(), "New item persisted");
    }
}
