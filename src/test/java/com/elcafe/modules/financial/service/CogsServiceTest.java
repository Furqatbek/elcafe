package com.elcafe.modules.financial.service;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CogsServiceTest {

    @Mock private ProductRepository productRepository;
    @InjectMocks private CogsService cogsService;

    private static Product product(Long id, String costPrice) {
        Product p = new Product();
        p.setId(id);
        p.setCostPrice(costPrice == null ? null : new BigDecimal(costPrice));
        return p;
    }

    private static OrderItem item(Long productId, int quantity) {
        OrderItem i = new OrderItem();
        i.setProductId(productId);
        i.setQuantity(quantity);
        return i;
    }

    private static Order order(OrderItem... items) {
        Order o = new Order();
        o.setItems(new ArrayList<>(List.of(items)));
        return o;
    }

    @Test @DisplayName("sums cost price x quantity across orders")
    void sumsAcrossOrders() {
        when(productRepository.findAllById(any()))
                .thenReturn(List.of(product(1L, "5000"), product(2L, "12000")));

        BigDecimal cogs = cogsService.calculateCogs(List.of(
                order(item(1L, 3)), order(item(2L, 2), item(1L, 1))));

        // 3x5000 + 2x12000 + 1x5000
        assertThat(cogs).isEqualByComparingTo("44000");
    }

    @Test @DisplayName("ignores bundle/packaging lines and deleted items")
    void ignoresNonProductAndDeletedLines() {
        when(productRepository.findAllById(any())).thenReturn(List.of(product(1L, "5000")));

        OrderItem deleted = item(1L, 10);
        deleted.softDelete("admin");

        BigDecimal cogs = cogsService.calculateCogs(List.of(
                order(item(1L, 2), item(null, 4), deleted)));

        assertThat(cogs).isEqualByComparingTo("10000");
    }

    @Test @DisplayName("a product with no maintained cost contributes nothing")
    void skipsProductsWithoutCost() {
        when(productRepository.findAllById(any()))
                .thenReturn(List.of(product(1L, null), product(2L, "0"), product(3L, "700")));

        BigDecimal cogs = cogsService.calculateCogs(List.of(
                order(item(1L, 5), item(2L, 5), item(3L, 5))));

        assertThat(cogs).isEqualByComparingTo("3500");
    }

    @Test @DisplayName("no orders is zero, not a failure")
    void emptyIsZero() {
        assertThat(cogsService.calculateCogs(List.of())).isEqualByComparingTo("0");
        assertThat(cogsService.calculateCogs(null)).isEqualByComparingTo("0");
    }
}
