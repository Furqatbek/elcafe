package com.elcafe.modules.financial.service;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cost of goods sold for a set of orders.
 *
 * <p>This is the cost side of the profit lines on the dashboard and the P&amp;L.
 * Stock is capitalised when it is bought — a received purchase order debits the
 * Inventory asset, it is not a period expense — so the cost of what was sold has
 * to be recognised here, at sale time, or it never reaches the books at all.
 *
 * <p>The basis is {@code Product.costPrice}, which ProductCostService keeps in
 * step with each product's recipe (or its production batches, for prepared
 * items). The analytics module computes a more precise figure from actual batch
 * consumption with FIFO/weighted-average costs; this one is the number the
 * dashboard already reports, available for every product whose cost is
 * maintained, and it is deliberately the single implementation behind both
 * profit lines so they cannot drift apart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CogsService {

    private final ProductRepository productRepository;

    /**
     * Sum the cost of every sold item across these orders.
     *
     * <p>Products are loaded in one query rather than per item. Lines with no
     * product (bundle and packaging rows) and products with no maintained cost
     * contribute nothing — a missing cost understates COGS, which is visible as
     * an implausibly high margin, rather than failing the whole report.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateCogs(Collection<Order> orders) {
        if (orders == null || orders.isEmpty()) return BigDecimal.ZERO;

        List<OrderItem> items = orders.stream()
                .filter(o -> o.getItems() != null)
                .flatMap(o -> o.getItems().stream())
                .filter(i -> !i.isDeleted() && i.getProductId() != null)
                .collect(Collectors.toList());

        if (items.isEmpty()) return BigDecimal.ZERO;

        Set<Long> productIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toSet());
        Map<Long, Product> products;
        try {
            products = productRepository.findAllById(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));
        } catch (Exception e) {
            log.warn("Could not load products for COGS over {} items: {}", items.size(), e.getMessage());
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            Product product = products.get(item.getProductId());
            if (product == null || product.getCostPrice() == null
                    || product.getCostPrice().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            total = total.add(product.getCostPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return total;
    }
}
