package com.elcafe.modules.menu.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.repository.ProductionBatchRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for calculating and updating product cost prices based on recipe ingredients.
 * Automatically syncs product.costPrice when recipe changes or ingredient costs change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCostService {

    private final ProductRepository productRepository;
    private final InventoryProductIngredientRepository productIngredientRepository;
    private final ProductionBatchRepository productionBatchRepository;

    /**
     * Recalculate and update cost price for a single product based on its ingredients.
     *
     * @param productId The product ID to update
     * @return The new calculated cost price
     */
    @Transactional
    public BigDecimal recalculateProductCost(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        BigDecimal newCostPrice;

        if (Boolean.TRUE.equals(product.getUsesProductionBatch())) {
            // Use average cost per unit from production batches
            BigDecimal avgCost = productionBatchRepository.getAverageCostPerUnit(productId);
            newCostPrice = (avgCost != null && avgCost.compareTo(BigDecimal.ZERO) > 0)
                    ? avgCost : product.getCostPrice();
        } else {
            // Existing recipe-based calculation
            newCostPrice = calculateCostFromIngredients(productId);
        }

        BigDecimal oldCostPrice = product.getCostPrice();
        product.setCostPrice(newCostPrice);
        productRepository.save(product);

        log.info("Updated product {} cost price: {} -> {}{}",
                product.getName(), oldCostPrice, newCostPrice,
                Boolean.TRUE.equals(product.getUsesProductionBatch()) ? " (from production batches)" : "");

        return newCostPrice;
    }

    /**
     * Calculate cost price from ingredients without saving.
     *
     * @param productId The product ID
     * @return The calculated cost price
     */
    public BigDecimal calculateCostFromIngredients(Long productId) {
        List<ProductIngredient> ingredients = productIngredientRepository.findByProductIdWithIngredients(productId);

        if (ingredients.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return ingredients.stream()
                .map(this::calculateIngredientCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate cost for a single ingredient usage.
     */
    private BigDecimal calculateIngredientCost(ProductIngredient pi) {
        Ingredient ingredient = pi.getIngredient();
        if (ingredient == null) {
            return BigDecimal.ZERO;
        }

        // Use effective cost (WAC if available, else costPerUnit)
        BigDecimal costPerUnit = ingredient.getEffectiveCost();
        if (costPerUnit == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal quantity = pi.getQuantityRequired();
        if (quantity == null) {
            return BigDecimal.ZERO;
        }

        return costPerUnit.multiply(quantity);
    }

    /**
     * Recalculate cost prices for all products that use a specific ingredient.
     * Call this when an ingredient's cost changes.
     *
     * @param ingredientId The ingredient ID whose cost changed
     * @return Number of products updated
     */
    @Transactional
    public int recalculateProductsUsingIngredient(Long ingredientId) {
        // Use query that eagerly fetches product to avoid LazyInitializationException
        List<ProductIngredient> usages = productIngredientRepository.findByIngredientIdWithProduct(ingredientId);

        int updatedCount = 0;
        for (ProductIngredient usage : usages) {
            Long productId = usage.getProduct().getId();
            recalculateProductCost(productId);
            updatedCount++;
        }

        log.info("Recalculated cost for {} products using ingredient {}", updatedCount, ingredientId);
        return updatedCount;
    }

    /**
     * Recalculate cost prices for all products in the system.
     * Uses bulk loading to avoid N+1 query performance issues.
     *
     * @return Number of products updated
     */
    @Transactional
    public int recalculateAllProductCosts() {
        // Load all products
        List<Product> allProducts = productRepository.findAll();
        Set<Long> productIds = allProducts.stream()
                .map(Product::getId)
                .collect(Collectors.toSet());

        // Bulk load all product ingredients with their ingredients in a single query
        List<ProductIngredient> allProductIngredients = productIngredientRepository.findByProductIdInWithIngredients(productIds);

        // Group product ingredients by product ID for efficient lookup
        Map<Long, List<ProductIngredient>> ingredientsByProductId = allProductIngredients.stream()
                .collect(Collectors.groupingBy(pi -> pi.getProduct().getId()));

        int updatedCount = 0;
        for (Product product : allProducts) {
            // Calculate cost using pre-loaded ingredients
            List<ProductIngredient> productIngredients = ingredientsByProductId.getOrDefault(product.getId(), List.of());
            BigDecimal newCostPrice = productIngredients.stream()
                    .map(this::calculateIngredientCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Only update if cost changed
            if (product.getCostPrice() == null ||
                product.getCostPrice().compareTo(newCostPrice) != 0) {

                product.setCostPrice(newCostPrice);
                productRepository.save(product);
                updatedCount++;

                log.debug("Updated product {} cost to {}", product.getName(), newCostPrice);
            }
        }

        log.info("Recalculated cost for {} products (total: {})", updatedCount, allProducts.size());
        return updatedCount;
    }

    /**
     * Get cost breakdown for a product showing each ingredient's contribution.
     *
     * @param productId The product ID
     * @return List of ingredient costs
     */
    public List<IngredientCostBreakdown> getCostBreakdown(Long productId) {
        List<ProductIngredient> ingredients = productIngredientRepository.findByProductIdWithIngredients(productId);

        return ingredients.stream()
                .map(pi -> {
                    Ingredient ing = pi.getIngredient();
                    return new IngredientCostBreakdown(
                            ing.getId(),
                            ing.getName(),
                            pi.getQuantityRequired(),
                            pi.getUnit() != null ? pi.getUnit() : ing.getUnit(),
                            ing.getEffectiveCost(),
                            calculateIngredientCost(pi)
                    );
                })
                .toList();
    }

    /**
     * Record for ingredient cost breakdown
     */
    public record IngredientCostBreakdown(
            Long ingredientId,
            String ingredientName,
            BigDecimal quantity,
            String unit,
            BigDecimal costPerUnit,
            BigDecimal totalCost
    ) {}
}
