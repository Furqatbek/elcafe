package com.elcafe.modules.waiter.entity;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.waiter.helper.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the Order items collection bug.
 *
 * OrderItem uses @EqualsAndHashCode(onlyExplicitlyIncluded = true) with only the {@code id}
 * field included. When items are newly created (id = null), they all have the same hashCode
 * and are considered equal. A HashSet would silently collapse them into a single entry.
 *
 * The fix changed Order.items from HashSet to ArrayList, ensuring all items are preserved
 * regardless of their id value.
 */
@DisplayName("Order items List regression (HashSet -> ArrayList)")
class OrderItemListRegressionTest {

    /**
     * The core regression case: multiple new items with null IDs must all be preserved.
     * With a HashSet this would have collapsed to 1 item because all null-id OrderItems
     * share the same equals/hashCode.
     */
    @Test
    @DisplayName("multiple new items with null IDs are all preserved")
    void multipleNewItems_withNullIds_allPreserved() {
        Order order = TestDataFactory.createOrder();

        for (long productId = 1; productId <= 5; productId++) {
            OrderItem item = TestDataFactory.createOrderItem(
                    productId, "Product " + productId, 1, BigDecimal.valueOf(10.00));
            order.addItem(item);
        }

        assertEquals(5, order.getItems().size(),
                "All 5 items with null IDs must be preserved in the list");
    }

    /**
     * Items that match on product, variant, addOns, specialInstructions, bundleId, and unitPrice
     * should be consolidated (quantity summed) rather than duplicated.
     */
    @Test
    @DisplayName("items with same product consolidate correctly")
    void multipleNewItems_sameProduct_consolidatesCorrectly() {
        Order order = TestDataFactory.createOrder();

        OrderItem item1 = TestDataFactory.createOrderItem(1L, "Espresso", 2, BigDecimal.valueOf(5.00));
        OrderItem item2 = TestDataFactory.createOrderItem(1L, "Espresso", 3, BigDecimal.valueOf(5.00));

        order.addItem(item1);
        order.addItem(item2);

        assertEquals(1, order.getItems().size(),
                "Identical items should be consolidated into one entry");
        assertEquals(5, order.getItems().get(0).getQuantity(),
                "Consolidated quantity should be the sum of both items (2 + 3)");
    }

    /**
     * Items for the same product but with different special instructions are NOT identical
     * and must be kept as separate line items.
     */
    @Test
    @DisplayName("same product with different special instructions kept separate")
    void multipleNewItems_sameProductDifferentInstructions_keptSeparate() {
        Order order = TestDataFactory.createOrder();

        OrderItem item1 = TestDataFactory.createOrderItem(1L, "Latte", 1, BigDecimal.valueOf(6.00));
        item1.setSpecialInstructions("Extra hot");

        OrderItem item2 = TestDataFactory.createOrderItem(1L, "Latte", 1, BigDecimal.valueOf(6.00));
        item2.setSpecialInstructions("Iced");

        order.addItem(item1);
        order.addItem(item2);

        assertEquals(2, order.getItems().size(),
                "Items with different specialInstructions must remain separate");
    }

    /**
     * Simulates what happens after JPA persistence assigns IDs to previously-null-id items.
     * With a HashSet, mutating the id field after insertion would corrupt the bucket placement
     * and make items unfindable. With an ArrayList this is safe.
     */
    @Test
    @DisplayName("items remain accessible after IDs are assigned (simulating persistence)")
    void itemsRemainAccessible_afterIdSimulation() {
        Order order = TestDataFactory.createOrder();

        OrderItem itemA = TestDataFactory.createOrderItem(10L, "Cappuccino", 1, BigDecimal.valueOf(7.00));
        OrderItem itemB = TestDataFactory.createOrderItem(20L, "Mocha", 1, BigDecimal.valueOf(8.00));

        order.addItem(itemA);
        order.addItem(itemB);

        // Simulate JPA assigning IDs after flush
        itemA.setId(100L);
        itemB.setId(200L);

        assertEquals(2, order.getItems().size(),
                "Both items must still be present after ID assignment");

        Optional<OrderItem> foundA = order.getItems().stream()
                .filter(i -> Long.valueOf(100L).equals(i.getId()))
                .findFirst();
        assertTrue(foundA.isPresent(), "Item A should be findable by its new ID");
        assertEquals("Cappuccino", foundA.get().getProductName());

        Optional<OrderItem> foundB = order.getItems().stream()
                .filter(i -> Long.valueOf(200L).equals(i.getId()))
                .findFirst();
        assertTrue(foundB.isPresent(), "Item B should be findable by its new ID");
        assertEquals("Mocha", foundB.get().getProductName());
    }

    /**
     * Removing an item by reference must work even after the item's id has changed.
     * A HashSet would fail to locate the element after its hashCode changed; an ArrayList
     * uses identity/equals which still matches the same object reference.
     */
    @Test
    @DisplayName("remove by reference works after ID changes")
    void removeItem_worksAfterIdChange() {
        Order order = TestDataFactory.createOrder();

        OrderItem item = TestDataFactory.createOrderItem(30L, "Americano", 1, BigDecimal.valueOf(4.00));
        order.addItem(item);

        assertEquals(1, order.getItems().size(), "Item should be present before removal");

        // Simulate JPA assigning an ID
        item.setId(300L);

        // Remove by the same object reference
        boolean removed = order.getItems().remove(item);

        assertTrue(removed, "remove() should return true for the same object reference");
        assertEquals(0, order.getItems().size(),
                "Items list should be empty after removing the only item");
    }
}
