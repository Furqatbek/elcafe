package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.enums.TransactionType;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryTransactionRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
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
    @Lazy
    private final InventoryValuationService valuationService;

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
            try {
                consumptionResult = valuationService.consumeWithValuation(
                        ingredientId, quantityRequired, order.getId());
                log.debug("Consumed {} of {} using {} method, total cost: {}",
                        consumptionResult.quantityConsumed(), ingredient.getName(),
                        consumptionResult.method(), consumptionResult.totalCost());
            } catch (Exception e) {
                log.warn("Failed to use valuation service for deduction, falling back to simple deduction: {}",
                        e.getMessage());
                // Fallback to simple deduction
                ingredient.deductStock(quantityRequired);
                ingredientRepository.save(ingredient);
            }

            BigDecimal balanceAfter = ingredient.getCurrentStock();

            // Record transaction with cost info if available
            InventoryTransaction.InventoryTransactionBuilder transactionBuilder = InventoryTransaction.builder()
                    .ingredient(ingredient)
                    .type(TransactionType.ORDER_DEDUCTION)
                    .quantity(quantityRequired)
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .referenceType("ORDER")
                    .referenceId(order.getId())
                    .notes("Deducted for order: " + order.getOrderNumber())
                    .performedBy("SYSTEM");

            // Add cost info from valuation if available
            if (consumptionResult != null) {
                transactionBuilder
                        .costPerUnit(consumptionResult.averageCostPerUnit())
                        .totalCost(consumptionResult.totalCost())
                        .valuationMethod(consumptionResult.method());
            }

            transactionRepository.save(transactionBuilder.build());

            log.info("Deducted {} {} of {} for order {}{}",
                    quantityRequired, ingredient.getUnit(), ingredient.getName(), order.getOrderNumber(),
                    consumptionResult != null ? " (cost: " + consumptionResult.totalCost() + ")" : "");
        }
    }

    /**
     * Calculate total required ingredients for an order
     */
    private Map<Long, BigDecimal> calculateRequiredIngredients(Order order) {
        Map<Long, BigDecimal> requiredIngredients = new HashMap<>();

        for (OrderItem item : order.getItems()) {
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
     * Add stock to an ingredient
     */
    @Transactional
    public void addStock(Long ingredientId, BigDecimal quantity, String notes, String performedBy) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

        BigDecimal balanceBefore = ingredient.getCurrentStock();
        ingredient.addStock(quantity);
        BigDecimal balanceAfter = ingredient.getCurrentStock();

        ingredientRepository.save(ingredient);

        // Record transaction
        InventoryTransaction transaction = InventoryTransaction.builder()
                .ingredient(ingredient)
                .type(TransactionType.PURCHASE)
                .quantity(quantity)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .notes(notes)
                .performedBy(performedBy)
                .build();

        transactionRepository.save(transaction);

        log.info("Added {} {} of {} by {}",
                quantity, ingredient.getUnit(), ingredient.getName(), performedBy);
    }

    /**
     * Adjust stock manually
     */
    @Transactional
    public void adjustStock(Long ingredientId, BigDecimal newQuantity, String reason, String performedBy) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

        BigDecimal balanceBefore = ingredient.getCurrentStock();
        BigDecimal difference = newQuantity.subtract(balanceBefore);

        ingredient.setCurrentStock(newQuantity);
        ingredientRepository.save(ingredient);

        // Record transaction
        InventoryTransaction transaction = InventoryTransaction.builder()
                .ingredient(ingredient)
                .type(TransactionType.ADJUSTMENT)
                .quantity(difference.abs())
                .balanceBefore(balanceBefore)
                .balanceAfter(newQuantity)
                .notes(reason)
                .performedBy(performedBy)
                .build();

        transactionRepository.save(transaction);

        log.info("Adjusted {} stock from {} to {} by {} (reason: {})",
                ingredient.getName(), balanceBefore, newQuantity, performedBy, reason);
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
