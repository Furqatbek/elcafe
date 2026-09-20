package com.elcafe.modules.order.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order.addItem() consolidates matching lines. Packaging lines carry no
 * productId, so they used to all look alike and collapse into one another.
 */
class OrderPackagingConsolidationTest {

    private static OrderItem packaging(String name, int qty) {
        return OrderItem.builder()
                .productId(null)
                .productName(name)
                .quantity(qty)
                .unitPrice(BigDecimal.ZERO)
                .totalPrice(BigDecimal.ZERO)
                .isPackagingItem(true)
                .build();
    }

    private static OrderItem product(Long productId, String name, int qty) {
        return OrderItem.builder()
                .productId(productId)
                .productName(name)
                .quantity(qty)
                .unitPrice(BigDecimal.ONE)
                .totalPrice(BigDecimal.ONE)
                .isPackagingItem(false)
                .build();
    }

    @Test
    @DisplayName("different packaging items stay separate (order 3922: ZeroMax + Qogoz, not ZeroMax x2)")
    void differentPackagingItemsAreNotMerged() {
        Order order = new Order();
        order.addItem(product(72L, "Redbull Moxito", 1));
        order.addItem(product(5L, "Cappucino 350ml", 1));
        order.addItem(packaging("ZeroMax 500ml", 1));
        order.addItem(packaging("Qogoz 350ml", 1));

        assertThat(order.getItems()).hasSize(4);
        assertThat(order.getItems())
                .filteredOn(i -> Boolean.TRUE.equals(i.getIsPackagingItem()))
                .extracting(OrderItem::getProductName, OrderItem::getQuantity)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("ZeroMax 500ml", 1),
                        org.assertj.core.groups.Tuple.tuple("Qogoz 350ml", 1));
    }

    @Test
    @DisplayName("identical packaging items still consolidate into one line")
    void identicalPackagingItemsAreMerged() {
        Order order = new Order();
        order.addItem(packaging("ZeroMax 500ml", 1));
        order.addItem(packaging("ZeroMax 500ml", 1));

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(order.getItems().get(0).getProductName()).isEqualTo("ZeroMax 500ml");
    }

    @Test
    @DisplayName("a packaging line never merges with a normal product line")
    void packagingNeverMergesWithProduct() {
        Order order = new Order();
        order.addItem(product(null, "Bundle thing", 1));
        order.addItem(packaging("ZeroMax 500ml", 1));

        assertThat(order.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("ordinary duplicate products still consolidate (unchanged behaviour)")
    void ordinaryDuplicatesStillMerge() {
        Order order = new Order();
        order.addItem(product(5L, "Cappucino 350ml", 1));
        order.addItem(product(5L, "Cappucino 350ml", 2));

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(3);
    }
}
