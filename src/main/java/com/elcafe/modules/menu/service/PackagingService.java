package com.elcafe.modules.menu.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
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
    private final InventoryIngredientRepository ingredientRepository;
    private final RestaurantRepository restaurantRepository;

    /**
     * Get packaging OrderItems to auto-add for a given list of order items and order type.
     * Packaging items reference inventory ingredients, shown on the order as line items.
     */
    public List<OrderItem> getPackagingItems(List<OrderItem> orderItems, OrderType orderType) {
        if (orderType == OrderType.DINE_IN) {
            return Collections.emptyList();
        }

        String orderTypeStr = orderType.name();
        List<OrderItem> packagingItems = new ArrayList<>();
        Set<Long> perOrderIngredientsAdded = new HashSet<>();

        for (OrderItem item : orderItems) {
            if (Boolean.TRUE.equals(item.getIsPackagingItem())) {
                continue;
            }

            List<PackagingRule> rules = packagingRuleRepository.findByProductIdAndActiveTrue(item.getProductId());

            for (PackagingRule rule : rules) {
                if (!rule.appliesToOrderType(orderTypeStr)) {
                    continue;
                }

                Long ingredientId = rule.getPackagingIngredient().getId();

                // PER_ORDER: skip if this ingredient was already added
                if (rule.getQuantityMode() == QuantityMode.PER_ORDER) {
                    if (perOrderIngredientsAdded.contains(ingredientId)) {
                        continue;
                    }
                    perOrderIngredientsAdded.add(ingredientId);
                }

                int qty;
                switch (rule.getQuantityMode()) {
                    case PER_ITEM -> qty = rule.getAutoAddQuantity() * item.getQuantity();
                    case PER_ORDER -> qty = rule.getAutoAddQuantity();
                    case FIXED -> qty = rule.getAutoAddQuantity();
                    default -> qty = rule.getAutoAddQuantity();
                }

                Ingredient ingredient = rule.getPackagingIngredient();
                BigDecimal unitCost = ingredient.getEffectiveCost();
                BigDecimal unitPrice = Boolean.TRUE.equals(rule.getChargeToCustomer())
                        ? unitCost
                        : BigDecimal.ZERO;

                OrderItem packagingItem = OrderItem.builder()
                        .productId(null)
                        .productName(ingredient.getName())
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .totalPrice(unitPrice.multiply(BigDecimal.valueOf(qty)))
                        .isPackagingItem(true)
                        .build();

                packagingItems.add(packagingItem);

                // Deduct from inventory
                try {
                    BigDecimal deductQty = BigDecimal.valueOf(qty);
                    if (ingredient.hasStock(deductQty)) {
                        ingredient.deductStock(deductQty);
                        ingredientRepository.save(ingredient);
                        log.debug("Deducted {}x {} from inventory for packaging", qty, ingredient.getName());
                    } else {
                        log.warn("Insufficient stock for packaging item {}: needed {}, available {}",
                                ingredient.getName(), qty, ingredient.getCurrentStock());
                    }
                } catch (Exception e) {
                    log.error("Failed to deduct packaging inventory for {}: {}",
                            ingredient.getName(), e.getMessage());
                }
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
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        Ingredient ingredient = ingredientRepository.findById(request.getPackagingIngredientId())
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));

        PackagingRule rule = PackagingRule.builder()
                .restaurant(restaurant)
                .product(product)
                .packagingIngredient(ingredient)
                .orderTypes(request.getOrderTypes())
                .quantityMode(QuantityMode.valueOf(request.getQuantityMode()))
                .autoAddQuantity(request.getAutoAddQuantity())
                .chargeToCustomer(request.getChargeToCustomer())
                .build();

        rule = packagingRuleRepository.save(rule);
        log.info("Created packaging rule: {} → ingredient {} for product {}",
                rule.getId(), ingredient.getName(), product.getName());
        return rule;
    }

    @Transactional
    public PackagingRule updateRule(Long ruleId, CreatePackagingRuleRequest request) {
        PackagingRule rule = packagingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Packaging rule not found"));

        if (request.getPackagingIngredientId() != null) {
            Ingredient ingredient = ingredientRepository.findById(request.getPackagingIngredientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));
            rule.setPackagingIngredient(ingredient);
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
                .orElseThrow(() -> new ResourceNotFoundException("Packaging rule not found"));
        rule.setActive(!rule.getActive());
        return packagingRuleRepository.save(rule);
    }
}
