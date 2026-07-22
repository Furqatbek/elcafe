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
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.enums.WaiterRole;
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
 * Integration test that verifies orders with items are correctly persisted
 * to the database. Uses H2 in-memory database via @DataJpaTest.
 *
 * This tests the actual JPA cascade behavior and proves that the
 * List<OrderItem> collection works correctly end-to-end.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class OrderCreationIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

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
        entityManager.persist(restaurant);

        table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber("T1");
        table.setTableName("Table 1");
        table.setStatus(TableStatus.AVAILABLE);
        table.setCapacity(4);
        table.setActive(true);
        entityManager.persist(table);

        waiter = new Waiter();
        waiter.setName("Test Waiter");
        waiter.setPinCode("1234");
        waiter.setRole(WaiterRole.WAITER);
        waiter.setActive(true);
        entityManager.persist(waiter);

        entityManager.flush();
    }

    @Test
    @DisplayName("Creating an order with multiple items persists ALL items to the database")
    void createOrder_withMultipleItems_allItemsPersistedToDatabase() {
        // Create order with 5 different items
        Order order = Order.builder()
                .orderNumber("TEST-001")
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

        // Add 5 items (all with id=null before persistence)
        for (int i = 1; i <= 5; i++) {
            OrderItem item = OrderItem.builder()
                    .productId((long) i)
                    .productName("Product " + i)
                    .quantity(i)
                    .unitPrice(BigDecimal.valueOf(10.00 * i))
                    .totalPrice(BigDecimal.valueOf(10.00 * i * i))
                    .build();
            order.addItem(item);
        }

        assertEquals(5, order.getItems().size(), "All 5 items should be in memory before save");

        // Save to database
        Order savedOrder = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear(); // Clear persistence context to force reload from DB

        // Reload from database
        Order reloaded = orderRepository.findById(savedOrder.getId()).orElseThrow();

        // Verify ALL items were persisted
        assertEquals(5, reloaded.getItems().size(),
                "All 5 items must be persisted to database — this was broken when items used HashSet");

        // Verify each item has a database-assigned ID
        for (OrderItem item : reloaded.getItems()) {
            assertNotNull(item.getId(), "Each item should have a database-assigned ID");
            assertNotNull(item.getProductName(), "Product name should be persisted");
        }
    }

    @Test
    @DisplayName("Creating an order with multiple items of the SAME product persists all separately")
    void createOrder_withDuplicateProducts_differentInstructions_allPersisted() {
        Order order = Order.builder()
                .orderNumber("TEST-002")
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

        // Same product, different special instructions — should NOT consolidate
        OrderItem item1 = OrderItem.builder()
                .productId(1L).productName("Espresso").quantity(1)
                .unitPrice(BigDecimal.valueOf(5.00)).totalPrice(BigDecimal.valueOf(5.00))
                .specialInstructions("Extra hot")
                .build();
        OrderItem item2 = OrderItem.builder()
                .productId(1L).productName("Espresso").quantity(1)
                .unitPrice(BigDecimal.valueOf(5.00)).totalPrice(BigDecimal.valueOf(5.00))
                .specialInstructions("With oat milk")
                .build();
        OrderItem item3 = OrderItem.builder()
                .productId(1L).productName("Espresso").quantity(2)
                .unitPrice(BigDecimal.valueOf(5.00)).totalPrice(BigDecimal.valueOf(10.00))
                .specialInstructions("No sugar")
                .build();

        order.addItem(item1);
        order.addItem(item2);
        order.addItem(item3);

        Order saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();

        assertEquals(3, reloaded.getItems().size(),
                "3 items with different instructions should all be persisted separately");
    }

    @Test
    @DisplayName("Adding items to an existing order persists the new items")
    void addItemsToExistingOrder_newItemsPersisted() {
        // Create order with 1 item
        Order order = Order.builder()
                .orderNumber("TEST-003")
                .restaurant(restaurant)
                .diningTable(table)
                .waiter(waiter)
                .status(OrderStatus.PREPARING)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .subtotal(BigDecimal.valueOf(10.00))
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(10.00))
                .items(new ArrayList<>())
                .build();

        OrderItem original = OrderItem.builder()
                .productId(1L).productName("Latte").quantity(1)
                .unitPrice(BigDecimal.valueOf(10.00)).totalPrice(BigDecimal.valueOf(10.00))
                .build();
        order.addItem(original);

        Order saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        // Reload and add 2 more items
        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(1, reloaded.getItems().size());

        OrderItem newItem1 = OrderItem.builder()
                .productId(2L).productName("Cappuccino").quantity(1)
                .unitPrice(BigDecimal.valueOf(8.00)).totalPrice(BigDecimal.valueOf(8.00))
                .build();
        OrderItem newItem2 = OrderItem.builder()
                .productId(3L).productName("Americano").quantity(2)
                .unitPrice(BigDecimal.valueOf(6.00)).totalPrice(BigDecimal.valueOf(12.00))
                .build();

        reloaded.addItem(newItem1);
        reloaded.addItem(newItem2);

        orderRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        // Reload again and verify all 3 items
        Order finalOrder = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(3, finalOrder.getItems().size(),
                "Original item + 2 new items should all be persisted");
    }

    @Test
    @DisplayName("Removing an item from an order actually deletes it from database")
    void removeItemFromOrder_itemDeletedFromDatabase() {
        Order order = Order.builder()
                .orderNumber("TEST-004")
                .restaurant(restaurant)
                .diningTable(table)
                .waiter(waiter)
                .status(OrderStatus.PREPARING)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .subtotal(BigDecimal.valueOf(15.00))
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(15.00))
                .items(new ArrayList<>())
                .build();

        OrderItem item1 = OrderItem.builder()
                .productId(1L).productName("Latte").quantity(1)
                .unitPrice(BigDecimal.valueOf(10.00)).totalPrice(BigDecimal.valueOf(10.00))
                .build();
        OrderItem item2 = OrderItem.builder()
                .productId(2L).productName("Cookie").quantity(1)
                .unitPrice(BigDecimal.valueOf(5.00)).totalPrice(BigDecimal.valueOf(5.00))
                .build();
        order.addItem(item1);
        order.addItem(item2);

        Order saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        // Reload, remove one item
        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(2, reloaded.getItems().size());

        OrderItem toRemove = reloaded.getItems().stream()
                .filter(i -> "Cookie".equals(i.getProductName()))
                .findFirst().orElseThrow();
        reloaded.getItems().remove(toRemove);

        orderRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        // Verify only 1 item remains
        Order finalOrder = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(1, finalOrder.getItems().size());
        assertEquals("Latte", finalOrder.getItems().get(0).getProductName());
    }

    @Test
    @DisplayName("Order items retain correct data after persistence round-trip")
    void orderItems_retainCorrectData_afterPersistence() {
        Order order = Order.builder()
                .orderNumber("TEST-005")
                .restaurant(restaurant)
                .diningTable(table)
                .waiter(waiter)
                .status(OrderStatus.NEW)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .subtotal(BigDecimal.valueOf(25.00))
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(25.00))
                .items(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder()
                .productId(42L)
                .productName("Special Burger")
                .quantity(3)
                .unitPrice(BigDecimal.valueOf(15.00))
                .totalPrice(BigDecimal.valueOf(45.00))
                .specialInstructions("No onions, extra cheese")
                .build();
        order.addItem(item);

        Order saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        OrderItem reloadedItem = reloaded.getItems().get(0);

        assertEquals(42L, reloadedItem.getProductId());
        assertEquals("Special Burger", reloadedItem.getProductName());
        assertEquals(3, reloadedItem.getQuantity());
        assertEquals(0, BigDecimal.valueOf(15.00).compareTo(reloadedItem.getUnitPrice()));
        assertEquals(0, BigDecimal.valueOf(45.00).compareTo(reloadedItem.getTotalPrice()));
        assertEquals("No onions, extra cheese", reloadedItem.getSpecialInstructions());
    }
}
