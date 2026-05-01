package com.elcafe.modules.menu.service;

import com.elcafe.modules.menu.dto.CreatePackagingRuleRequest;
import com.elcafe.modules.menu.entity.PackagingRule;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.QuantityMode;
import com.elcafe.modules.menu.repository.PackagingRuleRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PackagingService {

    private final PackagingRuleRepository packagingRuleRepository;
    private final ProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;

    /**
     * Get packaging OrderItems to auto-add for a given list of order items and order type.
     * Handles deduplication for PER_ORDER items.
     */
    public List<OrderItem> getPackagingItems(List<OrderItem> orderItems, OrderType orderType) {
        if (orderType == OrderType.DINE_IN) {
            return Collections.emptyList();
        }

        String orderTypeStr = orderType.name();
        List<OrderItem> packagingItems = new ArrayList<>();
        Set<Long> perOrderProductsAdded = new HashSet<>();

        for (OrderItem item : orderItems) {
            if (Boolean.TRUE.equals(item.getIsPackagingItem())) {
                continue;
            }

            List<PackagingRule> rules = packagingRuleRepository.findByProductIdAndActiveTrue(item.getProductId());

            for (PackagingRule rule : rules) {
                if (!rule.appliesToOrderType(orderTypeStr)) {
                    continue;
                }

                Long packagingProductId = rule.getPackagingProduct().getId();

                // PER_ORDER: skip if this packaging product was already added
                if (rule.getQuantityMode() == QuantityMode.PER_ORDER) {
                    if (perOrderProductsAdded.contains(packagingProductId)) {
                        continue;
                    }
                    perOrderProductsAdded.add(packagingProductId);
                }

                int qty;
                switch (rule.getQuantityMode()) {
                    case PER_ITEM -> qty = rule.getAutoAddQuantity() * item.getQuantity();
                    case PER_ORDER -> qty = rule.getAutoAddQuantity();
                    case FIXED -> qty = rule.getAutoAddQuantity();
                    default -> qty = rule.getAutoAddQuantity();
                }

                Product packagingProduct = rule.getPackagingProduct();
                BigDecimal unitPrice = Boolean.TRUE.equals(rule.getChargeToCustomer())
                        ? packagingProduct.getPrice()
                        : BigDecimal.ZERO;

                OrderItem packagingItem = OrderItem.builder()
                        .productId(packagingProduct.getId())
                        .productName(packagingProduct.getName())
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .totalPrice(unitPrice.multiply(BigDecimal.valueOf(qty)))
                        .isPackagingItem(true)
                        .build();

                packagingItems.add(packagingItem);

                log.debug("Auto-add packaging: {}x {} for product {} ({})",
                        qty, packagingProduct.getName(), item.getProductName(), rule.getQuantityMode());
            }
        }

        if (!packagingItems.isEmpty()) {
            log.info("Auto-added {} packaging item(s) for {} order",
                    packagingItems.size(), orderTypeStr);
        }

        return packagingItems;
    }

    @Transactional(readOnly = true)
    public List<PackagingRule> getRulesForProduct(Long productId) {
        return packagingRuleRepository.findByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<PackagingRule> getRulesForRestaurant(Long restaurantId) {
        return packagingRuleRepository.findByRestaurantId(restaurantId);
    }

    @Transactional
    public PackagingRule createRule(CreatePackagingRuleRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        Product packagingProduct = productRepository.findById(request.getPackagingProductId())
                .orElseThrow(() -> new RuntimeException("Packaging product not found"));

        PackagingRule rule = PackagingRule.builder()
                .restaurant(restaurant)
                .product(product)
                .packagingProduct(packagingProduct)
                .orderTypes(request.getOrderTypes())
                .quantityMode(QuantityMode.valueOf(request.getQuantityMode()))
                .autoAddQuantity(request.getAutoAddQuantity())
                .chargeToCustomer(request.getChargeToCustomer())
                .build();

        rule = packagingRuleRepository.save(rule);
        log.info("Created packaging rule: {} → {} for product {}",
                rule.getId(), packagingProduct.getName(), product.getName());
        return rule;
    }

    @Transactional
    public PackagingRule updateRule(Long ruleId, CreatePackagingRuleRequest request) {
        PackagingRule rule = packagingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Packaging rule not found"));

        if (request.getPackagingProductId() != null) {
            Product packagingProduct = productRepository.findById(request.getPackagingProductId())
                    .orElseThrow(() -> new RuntimeException("Packaging product not found"));
            rule.setPackagingProduct(packagingProduct);
        }
        if (request.getOrderTypes() != null) rule.setOrderTypes(request.getOrderTypes());
        if (request.getQuantityMode() != null) rule.setQuantityMode(QuantityMode.valueOf(request.getQuantityMode()));
        if (request.getAutoAddQuantity() != null) rule.setAutoAddQuantity(request.getAutoAddQuantity());
        if (request.getChargeToCustomer() != null) rule.setChargeToCustomer(request.getChargeToCustomer());

        return packagingRuleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(Long ruleId) {
        packagingRuleRepository.deleteById(ruleId);
        log.info("Deleted packaging rule {}", ruleId);
    }

    @Transactional
    public PackagingRule toggleRule(Long ruleId) {
        PackagingRule rule = packagingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Packaging rule not found"));
        rule.setActive(!rule.getActive());
        return packagingRuleRepository.save(rule);
    }
}
