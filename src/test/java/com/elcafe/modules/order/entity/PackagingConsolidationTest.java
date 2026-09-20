package com.elcafe.modules.order.entity;

import com.elcafe.modules.waiter.helper.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two different pieces of packaging must not become one.
 *
 * <p>Order 3922 was a Redbull Moxito and a Cappucino, which take a ZeroMax bottle and a Qogoz 350ml
 * cup. It was recorded as a single packaging line: <i>ZeroMax 500ml x2</i>. The cup was gone and its
 * quantity had been added to the bottle's.
 *
 * <p>The cause is that {@code itemsMatch} had nothing left to tell them apart. Packaging lines carry
 * no {@code productId}, no variant, no add-ons, no instructions and no bundle; and the last check,
 * unit price, is equal because packaging the venue absorbs is priced at zero. Every test passed, so
 * the second line was consolidated into the first.
 *
 * <p>Inventory was never wrong — {@code PackagingService} deducts each ingredient as it builds the
 * line, before any of this. Nor was the money: consolidation is only reached at equal unit prices,
 * so the total is the same either way. What was wrong was the record of what the customer was given,
 * which is what anyone auditing packaging usage against stock would read.
 */
@DisplayName("Packaging lines consolidate by what they are, not by having no product")
class PackagingConsolidationTest {

    private static OrderItem packaging(String name, int quantity, String unitPrice) {
        return OrderItem.builder()
                .productId(null)
                .productName(name)
                .quantity(quantity)
                .unitPrice(new BigDecimal(unitPrice))
                .totalPrice(new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity)))
                .isPackagingItem(true)
                .build();
    }

    @Test
    @DisplayName("a bottle and a cup stay two lines, as order 3922 should have been")
    void differentPackaging_isNotMerged() {
        Order order = TestDataFactory.createOrder();

        order.addItem(packaging("ZeroMax 500ml", 1, "0"));
        order.addItem(packaging("Qogoz 350ml", 1, "0"));

        assertThat(order.getItems())
                .extracting(OrderItem::getProductName, OrderItem::getQuantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ZeroMax 500ml", 1),
                        org.assertj.core.groups.Tuple.tuple("Qogoz 350ml", 1));
    }

    @Test
    @DisplayName("the same packaging still consolidates — two drinks, two cups, one line")
    void identicalPackaging_isStillMerged() {
        Order order = TestDataFactory.createOrder();

        // A cappuccino and a latte each take a 350ml cup. Two lines of one would be a worse record
        // than one line of two: the customer was handed two cups, not two separate orders of cup.
        order.addItem(packaging("Qogoz 350ml", 1, "0"));
        order.addItem(packaging("Qogoz 350ml", 1, "0"));

        assertThat(order.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getProductName()).isEqualTo("Qogoz 350ml");
                    assertThat(item.getQuantity()).isEqualTo(2);
                });
    }

    @Test
    @DisplayName("charged packaging separates by name too, not only free packaging")
    void chargedPackaging_isNotMerged() {
        Order order = TestDataFactory.createOrder();

        // Zero prices make the collision certain, but they are not what causes it: two ingredients
        // that happen to cost the same collide just as readily.
        order.addItem(packaging("ZeroMax 500ml", 1, "1500"));
        order.addItem(packaging("Qogoz 350ml", 1, "1500"));

        assertThat(order.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("the order total is the same whether lines merge or not")
    void totalIsUnchangedByTheFix() {
        Order merged = TestDataFactory.createOrder();
        merged.addItem(packaging("Qogoz 350ml", 1, "1500"));
        merged.addItem(packaging("Qogoz 350ml", 1, "1500"));

        Order separate = TestDataFactory.createOrder();
        separate.addItem(packaging("ZeroMax 500ml", 1, "1500"));
        separate.addItem(packaging("Qogoz 350ml", 1, "1500"));

        // Consolidation is only ever reached at equal unit prices, so it cannot move money. This is
        // why the bug never showed up in revenue and could sit there unnoticed.
        assertThat(sum(merged)).isEqualByComparingTo(sum(separate));
    }

    @Test
    @DisplayName("ordinary products are unaffected — the same dish twice is still one line")
    void productLines_stillConsolidate() {
        Order order = TestDataFactory.createOrder();

        order.addItem(TestDataFactory.createOrderItem(5L, "Cappucino 350ml", 1, new BigDecimal("22000")));
        order.addItem(TestDataFactory.createOrderItem(5L, "Cappucino 350ml", 1, new BigDecimal("22000")));

        assertThat(order.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getQuantity()).isEqualTo(2));
    }

    @Test
    @DisplayName("two products sharing a name are still told apart by their id")
    void sameNameDifferentProduct_isNotMerged() {
        Order order = TestDataFactory.createOrder();

        // The name check is a fallback for rows with no productId. It must not become the identity
        // of rows that have one — two venues' "Osh" are different dishes.
        order.addItem(TestDataFactory.createOrderItem(5L, "Osh", 1, new BigDecimal("30000")));
        order.addItem(TestDataFactory.createOrderItem(9L, "Osh", 1, new BigDecimal("30000")));

        assertThat(order.getItems()).hasSize(2);
    }

    private static BigDecimal sum(Order order) {
        return order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
