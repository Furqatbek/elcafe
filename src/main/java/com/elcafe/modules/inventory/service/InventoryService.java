package com.elcafe.modules.inventory.service;

import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.entity.PurchaseOrder;
import com.elcafe.modules.financial.entity.PurchaseOrderItem;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.repository.PurchaseOrderRepository;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.entity.Supplier;
import com.elcafe.modules.inventory.enums.TransactionType;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryTransactionRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.repository.SupplierRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.restaurant.entity.Restaurant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ExpenseRepository expenseRepository;
    private final SupplierRepository supplierRepository;
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
            boolean usedBatchConsumption = false;
            try {
                consumptionResult = valuationService.consumeWithValuation(
                        ingredientId, quantityRequired, order.getId());
                log.debug("Batch consumption result: consumed {} of {} using {} method, total cost: {}",
                        consumptionResult.quantityConsumed(), ingredient.getName(),
                        consumptionResult.method(), consumptionResult.totalCost());

                // Check if batch consumption actually consumed the required quantity
                if (consumptionResult.quantityConsumed().compareTo(quantityRequired) >= 0) {
                    usedBatchConsumption = true;
                    // Refresh ingredient to get updated stock after batch consumption
                    ingredient = ingredientRepository.findById(ingredientId).orElse(ingredient);
                } else {
                    log.warn("Batch consumption only consumed {} of {} required for {}. Falling back to simple deduction.",
                            consumptionResult.quantityConsumed(), quantityRequired, ingredient.getName());
                }
            } catch (Exception e) {
                log.warn("Failed to use valuation service for deduction: {}", e.getMessage());
            }

            // Fallback to simple deduction if batch consumption didn't work
            if (!usedBatchConsumption) {
                log.info("Using simple stock deduction for {} {} of {}",
                        quantityRequired, ingredient.getUnit(), ingredient.getName());
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

        log.info("Calculating required ingredients for order with {} items", order.getItems().size());

        for (OrderItem item : order.getItems()) {
            log.info("Looking up recipe for product ID: {} ({})", item.getProductId(), item.getProductName());

            List<ProductIngredient> productIngredients =
                    productIngredientRepository.findByProductIdWithIngredients(item.getProductId());

            if (productIngredients.isEmpty()) {
                log.warn("No recipe found for product ID: {} ({}). Inventory will NOT be deducted for this item.",
                        item.getProductId(), item.getProductName());
                continue;
            }

            log.info("Found {} ingredients in recipe for product: {}", productIngredients.size(), item.getProductName());

            for (ProductIngredient pi : productIngredients) {
                if (pi.getOptional()) {
                    log.debug("Skipping optional ingredient: {}", pi.getIngredient().getName());
                    continue;
                }

                Long ingredientId = pi.getIngredient().getId();
                BigDecimal quantityPerProduct = pi.getQuantityRequired();
                BigDecimal totalQuantity = quantityPerProduct.multiply(BigDecimal.valueOf(item.getQuantity()));

                log.info("Ingredient: {} - need {} {} x {} = {} {}",
                        pi.getIngredient().getName(),
                        quantityPerProduct, pi.getUnit(),
                        item.getQuantity(),
                        totalQuantity, pi.getUnit());

                requiredIngredients.merge(ingredientId, totalQuantity, BigDecimal::add);
            }
        }

        log.info("Total ingredients to deduct: {}", requiredIngredients.size());
        return requiredIngredients;
    }

    /**
     * Add stock to an ingredient (uses ingredient's effective cost)
     */
    @Transactional
    public void addStock(Long ingredientId, BigDecimal quantity, String notes, String performedBy) {
        addStock(ingredientId, quantity, null, null, notes, performedBy);
    }

    /**
     * Add stock to an ingredient with cost tracking (backward compatibility)
     */
    @Transactional
    public void addStock(Long ingredientId, BigDecimal quantity, BigDecimal costPerUnit,
                         String notes, String performedBy) {
        addStock(ingredientId, quantity, costPerUnit, null, notes, performedBy);
    }

    /**
     * Add stock to an ingredient with cost tracking and auto-create PurchaseOrder + Expense
     */
    @Transactional
    public void addStock(Long ingredientId, BigDecimal quantity, BigDecimal costPerUnit,
                         Long supplierId, String notes, String performedBy) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

        BigDecimal balanceBefore = ingredient.getCurrentStock();
        ingredient.addStock(quantity);
        BigDecimal balanceAfter = ingredient.getCurrentStock();

        // Use provided cost or ingredient's effective cost
        BigDecimal effectiveCost = costPerUnit != null ? costPerUnit : ingredient.getEffectiveCost();
        if (effectiveCost == null) {
            effectiveCost = BigDecimal.ZERO;
        }

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

        // Create PurchaseOrder and Expense for the stock addition
        BigDecimal totalCost = effectiveCost.multiply(quantity);
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            try {
                createPurchaseOrderAndExpense(ingredient, quantity, effectiveCost, supplierId, notes, performedBy);
            } catch (Exception e) {
                log.warn("Failed to create PurchaseOrder/Expense for stock addition: {}", e.getMessage());
            }
        }

        log.info("Added {} {} of {} by {} (cost: {})",
                quantity, ingredient.getUnit(), ingredient.getName(), performedBy, effectiveCost);
    }

    /**
     * Create a PurchaseOrder and Expense record for stock addition
     */
    private void createPurchaseOrderAndExpense(Ingredient ingredient, BigDecimal quantity,
                                                BigDecimal costPerUnit, Long supplierId,
                                                String notes, String performedBy) {
        Restaurant restaurant = ingredient.getRestaurant();
        LocalDate today = LocalDate.now();
        BigDecimal totalAmount = costPerUnit.multiply(quantity);

        // Get supplier info
        String supplierName = "Direct Purchase";
        Supplier supplier = null;
        if (supplierId != null) {
            supplier = supplierRepository.findById(supplierId).orElse(null);
            if (supplier != null) {
                supplierName = supplier.getName();
            }
        } else if (ingredient.getSupplierEntity() != null) {
            supplier = ingredient.getSupplierEntity();
            supplierName = supplier.getName();
        } else if (ingredient.getSupplier() != null && !ingredient.getSupplier().isEmpty()) {
            supplierName = ingredient.getSupplier();
        }

        // Generate PO number
        String poNumber = generatePoNumber(restaurant.getId());

        // Create PurchaseOrder
        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .restaurant(restaurant)
                .poNumber(poNumber)
                .supplier(supplier)
                .supplierName(supplierName)
                .orderDate(today)
                .expectedDeliveryDate(today)
                .actualDeliveryDate(today)
                .status(PurchaseOrder.Status.RECEIVED)
                .subtotal(totalAmount)
                .taxAmount(BigDecimal.ZERO)
                .shippingCost(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .paidAmount(BigDecimal.ZERO)
                .paymentStatus(PurchaseOrder.PaymentStatus.UNPAID)
                .notes(notes != null ? notes : "Auto-created from stock addition")
                .createdBy(performedBy)
                .receivedBy(performedBy)
                .receivedAt(LocalDateTime.now())
                .build();

        // Create PurchaseOrderItem
        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .purchaseOrder(purchaseOrder)
                .ingredient(ingredient)
                .itemName(ingredient.getName())
                .quantity(quantity)
                .unit(ingredient.getUnit())
                .unitPrice(costPerUnit)
                .totalPrice(totalAmount)
                .receivedQuantity(quantity)
                .build();

        purchaseOrder.getItems().add(poItem);
        PurchaseOrder savedPo = purchaseOrderRepository.save(purchaseOrder);

        log.info("Created PurchaseOrder {} for stock addition of {}", poNumber, ingredient.getName());

        // Create Expense
        String expenseNumber = generateExpenseNumber(restaurant.getId());
        Expense expense = Expense.builder()
                .restaurant(restaurant)
                .expenseNumber(expenseNumber)
                .expenseDate(today)
                .category(Expense.ExpenseCategory.INVENTORY)
                .description("Stock Purchase: " + ingredient.getName() + " (" + quantity + " " + ingredient.getUnit() + ")")
                .vendor(supplierName)
                .amount(totalAmount)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .paymentMethod(Expense.PaymentMethod.CASH)
                .paymentStatus(Expense.PaymentStatus.UNPAID)
                .referenceNumber(poNumber)
                .purchaseOrderId(savedPo.getId())
                .notes(notes)
                .createdBy(performedBy)
                .recurring(false)
                .build();

        expenseRepository.save(expense);

        log.info("Created Expense {} for stock addition of {}", expenseNumber, ingredient.getName());
    }

    private String generatePoNumber(Long restaurantId) {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long count = purchaseOrderRepository.findByRestaurant_Id(restaurantId).stream()
                .filter(po -> po.getPoNumber().startsWith("PO-" + datePrefix))
                .count();
        return String.format("PO-%s-%04d", datePrefix, count + 1);
    }

    private String generateExpenseNumber(Long restaurantId) {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long count = expenseRepository.findByRestaurant_Id(restaurantId).stream()
                .filter(exp -> exp.getExpenseNumber().startsWith("EXP-" + datePrefix))
                .count();
        return String.format("EXP-%s-%04d", datePrefix, count + 1);
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
