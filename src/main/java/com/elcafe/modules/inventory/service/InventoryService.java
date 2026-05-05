package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.enums.TransactionType;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryTransactionRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryIngredientRepository ingredientRepository;
    private final InventoryProductIngredientRepository productIngredientRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    @Lazy
    private final InventoryValuationService valuationService;
    @Lazy
    private final ProductionBatchService productionBatchService;
    @Lazy
    private final OwnerNotificationService ownerNotificationService;

    /**
     * Check if all ingredients are available for an order
     */
    @Transactional(readOnly = true)
    public boolean checkIngredientAvailability(Order order) {
        Map<Long, BigDecimal> requiredIngredients = calculateRequiredIngredients(order);

        for (Map.Entry<Long, BigDecimal> entry : requiredIngredients.entrySet()) {
            Ingredient ingredient = ingredientRepository.findById(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Ingredient not found: " + entry.getKey()));

            if (!ingredient.hasStock(entry.getValue())) {
                log.warn("Insufficient stock for ingredient: {} (required: {}, available: {})",
                        ingredient.getName(), entry.getValue(), ingredient.getCurrentStock());
                return false;
            }
        }

        return true;
    }

    /**
     * Deduct ingredients for an order using configured valuation method
     * This integrates with the batch consumption tracking for accurate COGS
     */
    @Transactional
    public void deductIngredientsForOrder(Order order) {
        log.info("Deducting ingredients for order: {}", order.getOrderNumber());

        // Handle production batch items first — deduct from prepared inventory
        for (OrderItem item : order.getItems()) {
            if (item.getProductId() == null) continue;
            try {
                Product product = productRepository.findById(item.getProductId()).orElse(null);
                if (product != null && Boolean.TRUE.equals(product.getUsesProductionBatch())) {
                    // Look up variant's batch deduction quantity
                    BigDecimal batchDeductionQty = null;
                    if (item.getVariantId() != null) {
                        ProductVariant variant = productVariantRepository.findById(item.getVariantId()).orElse(null);
                        if (variant != null && variant.getBatchDeductionQuantity() != null) {
                            batchDeductionQty = variant.getBatchDeductionQuantity();
                        }
                    }

                    productionBatchService.consumeForOrder(
                            product.getId(), item.getQuantity(),
                            item.getWeightAmount(), batchDeductionQty,
                            order.getId(), item.getId());
                    log.info("Deducted from production batch for product {} in order {}",
                            product.getName(), order.getOrderNumber());
                }
            } catch (Exception e) {
                log.error("Failed to deduct from production batch for item {} in order {}: {}",
                        item.getProductId(), order.getOrderNumber(), e.getMessage());
                throw new RuntimeException("Production batch deduction failed: " + e.getMessage(), e);
            }
        }

        // Calculate required raw ingredients (only for non-production-batch items)
        Map<Long, BigDecimal> requiredIngredients = calculateRequiredIngredients(order);

        for (Map.Entry<Long, BigDecimal> entry : requiredIngredients.entrySet()) {
            Long ingredientId = entry.getKey();
            BigDecimal quantityRequired = entry.getValue();

            Ingredient ingredient = ingredientRepository.findById(ingredientId)
                    .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

            // Check if enough stock is available
            if (!ingredient.hasStock(quantityRequired)) {
                throw new RuntimeException(String.format(
                        "Insufficient stock for ingredient: %s (required: %s, available: %s)",
                        ingredient.getName(), quantityRequired, ingredient.getCurrentStock()
                ));
            }

            BigDecimal balanceBefore = ingredient.getCurrentStock();

            // Try to use valuation service for batch-based consumption
            InventoryValuationService.ConsumptionResult consumptionResult = null;
            boolean valuationFailed = false;
            String valuationFailureReason = null;

            try {
                consumptionResult = valuationService.consumeWithValuation(
                        ingredientId, quantityRequired, order.getId());
                log.debug("Consumed {} of {} using {} method, total cost: {}",
                        consumptionResult.quantityConsumed(), ingredient.getName(),
                        consumptionResult.method(), consumptionResult.totalCost());
            } catch (Exception e) {
                valuationFailed = true;
                valuationFailureReason = e.getMessage();
                log.error("COGS TRACKING DEGRADED: Valuation service failed for ingredient {} (order {}). " +
                        "Falling back to simple deduction - batch-level cost tracking will be lost. Error: {}",
                        ingredient.getName(), order.getOrderNumber(), e.getMessage());

                // Fallback to simple deduction - but track the failure
                ingredient.deductStock(quantityRequired);
                ingredientRepository.save(ingredient);
            }

            BigDecimal balanceAfter = ingredient.getCurrentStock();

            // Build notes with failure tracking
            String transactionNotes = "Deducted for order: " + order.getOrderNumber();
            if (valuationFailed) {
                transactionNotes += " [VALUATION_FAILED: " + valuationFailureReason + "]";
            }

            // Record transaction with cost info if available
            InventoryTransaction.InventoryTransactionBuilder transactionBuilder = InventoryTransaction.builder()
                    .ingredient(ingredient)
                    .type(TransactionType.ORDER_DEDUCTION)
                    .quantity(quantityRequired)
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .referenceType("ORDER")
                    .referenceId(order.getId())
                    .notes(transactionNotes)
                    .performedBy("SYSTEM");

            // Add cost info from valuation if available
            if (consumptionResult != null) {
                transactionBuilder
                        .costPerUnit(consumptionResult.averageCostPerUnit())
                        .totalCost(consumptionResult.totalCost())
                        .valuationMethod(consumptionResult.method());
            } else {
                // Use ingredient's effective cost as fallback for tracking purposes
                BigDecimal fallbackCost = ingredient.getEffectiveCost();
                transactionBuilder
                        .costPerUnit(fallbackCost)
                        .totalCost(fallbackCost.multiply(quantityRequired))
                        .valuationMethod(ValuationMethod.WEIGHTED_AVERAGE); // Indicate it's WAC fallback
            }

            transactionRepository.save(transactionBuilder.build());

            log.info("Deducted {} {} of {} for order {}{}",
                    quantityRequired, ingredient.getUnit(), ingredient.getName(), order.getOrderNumber(),
                    consumptionResult != null ? " (cost: " + consumptionResult.totalCost() + ")" : "");

            // Check for low stock and send alert - use ingredient's restaurant ID since order.getRestaurant() may be null due to lazy loading
            checkAndNotifyLowStock(ingredient, ingredient.getRestaurant().getId());
        }
    }

    /**
     * Check if ingredient is below minimum stock and send notification
     */
    private void checkAndNotifyLowStock(Ingredient ingredient, Long restaurantId) {
        try {
            if (ownerNotificationService != null && ingredient.isLowStock()) {
                BigDecimal threshold = ingredient.getMinimumStock() != null
                        ? ingredient.getMinimumStock()
                        : BigDecimal.TEN;
                ownerNotificationService.notifyLowStock(
                        restaurantId,
                        ingredient.getName(),
                        ingredient.getCurrentStock().intValue(),
                        threshold.intValue()
                );
                log.info("Low stock alert sent for ingredient: {}", ingredient.getName());
            }
        } catch (Exception e) {
            log.error("Failed to send low stock notification for {}: {}", ingredient.getName(), e.getMessage());
        }
    }

    /**
     * Calculate total required ingredients for an order
     */
    private Map<Long, BigDecimal> calculateRequiredIngredients(Order order) {
        Map<Long, BigDecimal> requiredIngredients = new HashMap<>();

        for (OrderItem item : order.getItems()) {
            if (item.getProductId() == null) continue;
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            if (product != null && Boolean.TRUE.equals(product.getUsesProductionBatch())) {
                continue;
            }

            List<ProductIngredient> productIngredients =
                    productIngredientRepository.findByProductIdWithIngredients(item.getProductId());

            for (ProductIngredient pi : productIngredients) {
                if (pi.getOptional()) {
                    continue; // Skip optional ingredients
                }

                Long ingredientId = pi.getIngredient().getId();
                BigDecimal quantityPerProduct = pi.getQuantityRequired();
                BigDecimal totalQuantity = quantityPerProduct.multiply(BigDecimal.valueOf(item.getQuantity()));

                requiredIngredients.merge(ingredientId, totalQuantity, BigDecimal::add);
            }
        }

        return requiredIngredients;
    }

    /**
     * Add stock to an ingredient (uses ingredient's effective cost)
     */
    @Transactional
    public void addStock(Long ingredientId, BigDecimal quantity, String notes, String performedBy) {
        addStock(ingredientId, quantity, null, notes, performedBy);
    }

    /**
     * Add stock to an ingredient with cost tracking
     */
    @Transactional
    public void addStock(Long ingredientId, BigDecimal quantity, BigDecimal costPerUnit,
                         String notes, String performedBy) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

        BigDecimal balanceBefore = ingredient.getCurrentStock();
        ingredient.addStock(quantity);
        BigDecimal balanceAfter = ingredient.getCurrentStock();

        // Use provided cost or ingredient's effective cost
        BigDecimal effectiveCost = costPerUnit != null ? costPerUnit : ingredient.getEffectiveCost();

        // Update WAC if cost is provided
        if (costPerUnit != null && costPerUnit.compareTo(BigDecimal.ZERO) > 0) {
            ingredient.updateWeightedAverageCost(quantity, costPerUnit);
        }

        ingredientRepository.save(ingredient);

        // Record transaction with cost
        InventoryTransaction transaction = InventoryTransaction.builder()
                .ingredient(ingredient)
                .type(TransactionType.PURCHASE)
                .quantity(quantity)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .costPerUnit(effectiveCost)
                .totalCost(effectiveCost.multiply(quantity))
                .notes(notes)
                .performedBy(performedBy)
                .build();

        transactionRepository.save(transaction);

        log.info("Added {} {} of {} by {} (cost: {})",
                quantity, ingredient.getUnit(), ingredient.getName(), performedBy, effectiveCost);
    }

    /**
     * Adjust stock manually with cost tracking using effective cost (WAC or costPerUnit)
     */
    @Transactional
    public void adjustStock(Long ingredientId, BigDecimal newQuantity, String reason, String performedBy) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

        BigDecimal balanceBefore = ingredient.getCurrentStock();
        BigDecimal difference = newQuantity.subtract(balanceBefore);

        ingredient.setCurrentStock(newQuantity);
        ingredientRepository.save(ingredient);

        // Use ingredient's effective cost (WAC if available, else costPerUnit)
        BigDecimal effectiveCost = ingredient.getEffectiveCost();
        BigDecimal totalCostImpact = effectiveCost.multiply(difference.abs());

        // Record transaction with cost information
        InventoryTransaction transaction = InventoryTransaction.builder()
                .ingredient(ingredient)
                .type(TransactionType.ADJUSTMENT)
                .quantity(difference.abs())
                .balanceBefore(balanceBefore)
                .balanceAfter(newQuantity)
                .costPerUnit(effectiveCost)
                .totalCost(totalCostImpact)
                .notes(reason)
                .performedBy(performedBy)
                .build();

        transactionRepository.save(transaction);

        log.info("Adjusted {} stock from {} to {} by {} (reason: {}, cost impact: {})",
                ingredient.getName(), balanceBefore, newQuantity, performedBy, reason, totalCostImpact);
    }

    /**
     * Get low stock ingredients
     */
    @Transactional(readOnly = true)
    public List<Ingredient> getLowStockIngredients(Long restaurantId) {
        return ingredientRepository.findLowStockIngredients(restaurantId);
    }

    /**
     * Get ingredients needing reorder
     */
    @Transactional(readOnly = true)
    public List<Ingredient> getIngredientsNeedingReorder(Long restaurantId) {
        return ingredientRepository.findIngredientsNeedingReorder(restaurantId);
    }

    /**
     * Get transaction history for an ingredient
     */
    @Transactional(readOnly = true)
    public List<InventoryTransaction> getTransactionHistory(Long ingredientId) {
        return transactionRepository.findByIngredientIdOrderByCreatedAtDesc(ingredientId);
    }

    /**
     * Get transaction history for a date range
     */
    @Transactional(readOnly = true)
    public List<InventoryTransaction> getTransactionHistoryByDateRange(
            Long restaurantId, LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.findByRestaurantAndDateRange(restaurantId, startDate, endDate);
    }

    /**
     * Check if a product can be made with current stock
     */
    @Transactional(readOnly = true)
    public boolean canMakeProduct(Long productId, int quantity) {
        List<ProductIngredient> requiredIngredients =
                productIngredientRepository.findByProductIdWithIngredients(productId);

        for (ProductIngredient pi : requiredIngredients) {
            if (pi.getOptional()) {
                continue;
            }

            Ingredient ingredient = pi.getIngredient();
            BigDecimal required = pi.getQuantityRequired().multiply(BigDecimal.valueOf(quantity));

            if (!ingredient.hasStock(required)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get missing ingredients for a product
     */
    @Transactional(readOnly = true)
    public List<String> getMissingIngredients(Long productId, int quantity) {
        List<String> missingIngredients = new ArrayList<>();
        List<ProductIngredient> requiredIngredients =
                productIngredientRepository.findByProductIdWithIngredients(productId);

        for (ProductIngredient pi : requiredIngredients) {
            if (pi.getOptional()) {
                continue;
            }

            Ingredient ingredient = pi.getIngredient();
            BigDecimal required = pi.getQuantityRequired().multiply(BigDecimal.valueOf(quantity));

            if (!ingredient.hasStock(required)) {
                missingIngredients.add(String.format("%s (need: %s %s, have: %s %s)",
                        ingredient.getName(),
                        required, ingredient.getUnit(),
                        ingredient.getCurrentStock(), ingredient.getUnit()));
            }
        }

        return missingIngredients;
    }
}
