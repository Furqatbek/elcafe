package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.dto.*;
import com.elcafe.modules.inventory.entity.*;
import com.elcafe.modules.inventory.enums.TransactionType;
import com.elcafe.modules.inventory.repository.*;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionBatchService {

    private final ProductionBatchRepository productionBatchRepository;
    private final ProductionBatchInputRepository productionBatchInputRepository;
    private final ProductionBatchConsumptionRepository productionBatchConsumptionRepository;
    @Lazy
    private final InventoryValuationService valuationService;
    private final InventoryIngredientRepository ingredientRepository;
    private final InventoryProductIngredientRepository productIngredientRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;

    private static final AtomicLong batchSequence = new AtomicLong(System.currentTimeMillis() % 100000);

    /**
     * Create a DRAFT production batch, optionally loading recipe as planned inputs
     */
    @Transactional
    public ProductionBatch createBatch(CreateProductionBatchRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found: " + request.getRestaurantId()));

        Product product = null;
        if (request.getProductId() != null) {
            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + request.getProductId()));
        }

        String batchNumber = generateBatchNumber();

        ProductionBatch batch = ProductionBatch.builder()
                .restaurant(restaurant)
                .product(product)
                .batchNumber(batchNumber)
                .name(request.getName())
                .outputUnit(request.getOutputUnit())
                .expiresAt(request.getExpiresAt())
                .notes(request.getNotes())
                .preparedBy(request.getPreparedBy())
                .status(ProductionBatch.Status.DRAFT)
                .build();

        batch = productionBatchRepository.save(batch);

        // Load recipe ingredients as planned inputs
        if (Boolean.TRUE.equals(request.getLoadRecipe()) && request.getProductId() != null) {
            loadRecipeInputs(batch, request.getProductId());
        }

        log.info("Created production batch {} '{}' for restaurant {}",
                batchNumber, request.getName(), request.getRestaurantId());

        return batch;
    }

    /**
     * Add or update an ingredient input for a batch
     */
    @Transactional
    public ProductionBatchInput addInput(Long batchId, AddInputRequest request) {
        ProductionBatch batch = getBatchOrThrow(batchId);
        validateBatchModifiable(batch);

        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new RuntimeException("Ingredient not found: " + request.getIngredientId()));

        BigDecimal costPerUnit = ingredient.getEffectiveCost();
        String unit = request.getUnit() != null ? request.getUnit() : ingredient.getUnit();

        ProductionBatchInput input = ProductionBatchInput.builder()
                .productionBatch(batch)
                .ingredient(ingredient)
                .actualQuantity(request.getActualQuantity())
                .unit(unit)
                .costPerUnit(costPerUnit)
                .totalCost(costPerUnit.multiply(request.getActualQuantity()))
                .notes(request.getNotes())
                .build();

        input = productionBatchInputRepository.save(input);

        log.info("Added input {} {} of {} to batch {}",
                request.getActualQuantity(), unit, ingredient.getName(), batch.getBatchNumber());

        return input;
    }

    /**
     * Update an existing ingredient input
     */
    @Transactional
    public ProductionBatchInput updateInput(Long batchId, Long inputId, AddInputRequest request) {
        ProductionBatch batch = getBatchOrThrow(batchId);
        validateBatchModifiable(batch);

        ProductionBatchInput input = productionBatchInputRepository.findById(inputId)
                .orElseThrow(() -> new RuntimeException("Production batch input not found: " + inputId));

        if (!input.getProductionBatch().getId().equals(batchId)) {
            throw new IllegalArgumentException("Input " + inputId + " does not belong to batch " + batchId);
        }

        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new RuntimeException("Ingredient not found: " + request.getIngredientId()));

        BigDecimal costPerUnit = ingredient.getEffectiveCost();
        String unit = request.getUnit() != null ? request.getUnit() : ingredient.getUnit();

        input.setIngredient(ingredient);
        input.setActualQuantity(request.getActualQuantity());
        input.setUnit(unit);
        input.setCostPerUnit(costPerUnit);
        input.setTotalCost(costPerUnit.multiply(request.getActualQuantity()));
        input.setNotes(request.getNotes());

        input = productionBatchInputRepository.save(input);

        log.info("Updated input {} for batch {}: {} {} of {}",
                inputId, batch.getBatchNumber(), request.getActualQuantity(), unit, ingredient.getName());

        return input;
    }

    /**
     * Start production — sets status to IN_PROGRESS
     */
    @Transactional
    public ProductionBatch startBatch(Long batchId) {
        ProductionBatch batch = getBatchOrThrow(batchId);

        if (batch.getStatus() != ProductionBatch.Status.DRAFT) {
            throw new IllegalStateException("Can only start a DRAFT batch, current status: " + batch.getStatus());
        }

        batch.setStatus(ProductionBatch.Status.IN_PROGRESS);
        batch.setStartedAt(LocalDateTime.now());
        batch = productionBatchRepository.save(batch);

        log.info("Started production batch {}", batch.getBatchNumber());
        return batch;
    }

    /**
     * Complete a batch: set output qty, deduct raw ingredients, calculate cost per unit, set READY.
     *
     * Key logic:
     * 1. Load all ProductionBatchInput records
     * 2. For each input: consume raw ingredients via InventoryValuationService
     * 3. Sum all input costs → total_input_cost
     * 4. cost_per_unit = total_input_cost / output_quantity
     * 5. remaining_quantity = output_quantity
     * 6. status = READY
     */
    @Transactional
    public ProductionBatch completeBatch(Long batchId, CompleteBatchRequest request) {
        ProductionBatch batch = getBatchOrThrow(batchId);

        if (batch.getStatus() != ProductionBatch.Status.DRAFT
                && batch.getStatus() != ProductionBatch.Status.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Can only complete a DRAFT or IN_PROGRESS batch, current status: " + batch.getStatus());
        }

        List<ProductionBatchInput> inputs = productionBatchInputRepository.findByProductionBatchId(batchId);
        if (inputs.isEmpty()) {
            throw new IllegalStateException("Cannot complete batch with no inputs");
        }

        BigDecimal totalInputCost = BigDecimal.ZERO;

        // Deduct raw ingredients and calculate costs
        for (ProductionBatchInput input : inputs) {
            try {
                InventoryValuationService.ConsumptionResult result =
                        valuationService.consumeWithValuation(
                                input.getIngredient().getId(),
                                input.getActualQuantity(),
                                null); // no order ID — this is production consumption

                // Update input with actual cost from valuation
                input.setCostPerUnit(result.averageCostPerUnit());
                input.setTotalCost(result.totalCost());
                productionBatchInputRepository.save(input);

                totalInputCost = totalInputCost.add(result.totalCost());

                // Record inventory transaction for production input
                Ingredient ingredient = input.getIngredient();
                InventoryTransaction transaction = InventoryTransaction.builder()
                        .ingredient(ingredient)
                        .type(TransactionType.PRODUCTION_INPUT)
                        .quantity(input.getActualQuantity())
                        .balanceBefore(ingredient.getCurrentStock().add(input.getActualQuantity()))
                        .balanceAfter(ingredient.getCurrentStock())
                        .costPerUnit(result.averageCostPerUnit())
                        .totalCost(result.totalCost())
                        .valuationMethod(result.method())
                        .referenceType("PRODUCTION_BATCH")
                        .referenceId(batch.getId())
                        .notes("Production input for batch: " + batch.getBatchNumber())
                        .performedBy(batch.getPreparedBy() != null ? batch.getPreparedBy() : "SYSTEM")
                        .build();
                transactionRepository.save(transaction);

                log.debug("Consumed {} {} of {} for batch {} (cost: {})",
                        input.getActualQuantity(), input.getUnit(),
                        ingredient.getName(), batch.getBatchNumber(), result.totalCost());

            } catch (Exception e) {
                log.error("Failed to consume ingredient {} for batch {}: {}",
                        input.getIngredient().getName(), batch.getBatchNumber(), e.getMessage());
                throw new RuntimeException("Failed to consume ingredient " +
                        input.getIngredient().getName() + ": " + e.getMessage(), e);
            }
        }

        // Set output and cost
        BigDecimal outputQuantity = request.getOutputQuantity();
        if (request.getOutputUnit() != null) {
            batch.setOutputUnit(request.getOutputUnit());
        }

        batch.setOutputQuantity(outputQuantity);
        batch.setRemainingQuantity(outputQuantity);
        batch.setTotalInputCost(totalInputCost);
        batch.setCostPerUnit(totalInputCost.divide(outputQuantity, 4, RoundingMode.HALF_UP));
        batch.setStatus(ProductionBatch.Status.READY);
        batch.setCompletedAt(LocalDateTime.now());

        if (request.getNotes() != null) {
            batch.setNotes(request.getNotes());
        }

        batch = productionBatchRepository.save(batch);

        log.info("Completed batch {} — output: {} {}, cost/unit: {}, total cost: {}",
                batch.getBatchNumber(), outputQuantity, batch.getOutputUnit(),
                batch.getCostPerUnit(), totalInputCost);

        return batch;
    }

    /**
     * Consume from a specific batch — deducts remaining quantity, creates consumption record
     */
    @Transactional
    public BigDecimal consumeFromBatch(Long batchId, BigDecimal quantity,
                                       Long orderId, Long orderItemId) {
        ProductionBatch batch = getBatchOrThrow(batchId);

        if (!batch.isAvailable()) {
            throw new IllegalStateException("Batch " + batch.getBatchNumber() + " is not available for consumption");
        }

        BigDecimal cost = batch.consume(quantity);
        productionBatchRepository.save(batch);

        ProductionBatchConsumption consumption = ProductionBatchConsumption.builder()
                .productionBatch(batch)
                .orderId(orderId)
                .orderItemId(orderItemId)
                .quantity(quantity)
                .costPerUnit(batch.getCostPerUnit())
                .totalCost(cost)
                .consumedAt(LocalDateTime.now())
                .build();
        productionBatchConsumptionRepository.save(consumption);

        log.info("Consumed {} {} from batch {} for order {} (cost: {})",
                quantity, batch.getOutputUnit(), batch.getBatchNumber(), orderId, cost);

        return cost;
    }

    /**
     * Consume for an order — finds the best available batch (FEFO) and deducts
     */
    @Transactional
    public BigDecimal consumeForOrder(Long productId, Integer itemQuantity,
                                      BigDecimal weightAmount, Long orderId, Long orderItemId) {
        // Determine the quantity to consume
        BigDecimal quantity;
        if (weightAmount != null && weightAmount.compareTo(BigDecimal.ZERO) > 0) {
            quantity = weightAmount;
        } else {
            quantity = BigDecimal.valueOf(itemQuantity != null ? itemQuantity : 1);
        }

        ProductionBatch batch = findAvailableBatch(productId);
        if (batch == null) {
            throw new RuntimeException("No available production batch for product: " + productId);
        }

        return consumeFromBatch(batch.getId(), quantity, orderId, orderItemId);
    }

    /**
     * Find best available batch for a product (FEFO — First Expired, First Out)
     */
    @Transactional(readOnly = true)
    public ProductionBatch findAvailableBatch(Long productId) {
        List<ProductionBatch> available = productionBatchRepository.findAvailableByProduct(productId);
        return available.isEmpty() ? null : available.get(0);
    }

    /**
     * Record waste/leftover from a batch
     */
    @Transactional
    public void recordWaste(Long batchId, BigDecimal quantity, String reason) {
        ProductionBatch batch = getBatchOrThrow(batchId);

        if (quantity.compareTo(batch.getRemainingQuantity()) > 0) {
            quantity = batch.getRemainingQuantity();
        }

        batch.setRemainingQuantity(batch.getRemainingQuantity().subtract(quantity));

        if (batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            batch.setStatus(ProductionBatch.Status.WASTED);
        }

        productionBatchRepository.save(batch);

        log.info("Recorded waste of {} {} from batch {} (reason: {})",
                quantity, batch.getOutputUnit(), batch.getBatchNumber(), reason);
    }

    /**
     * Get active (READY/SERVING) batches for a restaurant
     */
    @Transactional(readOnly = true)
    public List<ProductionBatch> getActiveBatches(Long restaurantId) {
        return productionBatchRepository.findActiveBatches(restaurantId);
    }

    /**
     * Get all batches for a restaurant
     */
    @Transactional(readOnly = true)
    public List<ProductionBatch> getBatchesByRestaurant(Long restaurantId) {
        return productionBatchRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    /**
     * Get batches by status
     */
    @Transactional(readOnly = true)
    public List<ProductionBatch> getBatchesByStatus(Long restaurantId, ProductionBatch.Status status) {
        return productionBatchRepository.findByRestaurantIdAndStatus(restaurantId, status);
    }

    /**
     * Get a batch by ID with full details
     */
    @Transactional(readOnly = true)
    public ProductionBatch getBatchById(Long batchId) {
        ProductionBatch batch = getBatchOrThrow(batchId);
        // Eagerly initialize lazy collections for use outside transaction
        batch.getInputs().size();
        batch.getConsumptions().size();
        return batch;
    }

    /**
     * Delete a DRAFT batch
     */
    @Transactional
    public void deleteBatch(Long batchId) {
        ProductionBatch batch = getBatchOrThrow(batchId);
        if (batch.getStatus() != ProductionBatch.Status.DRAFT) {
            throw new IllegalStateException("Can only delete DRAFT batches, current status: " + batch.getStatus());
        }
        productionBatchRepository.delete(batch);
        log.info("Deleted draft batch {}", batch.getBatchNumber());
    }

    /**
     * Production cost report for a restaurant within a date range
     */
    @Transactional(readOnly = true)
    public List<Object[]> getBatchCostReport(Long restaurantId, LocalDate from, LocalDate to) {
        LocalDateTime startDate = from.atStartOfDay();
        LocalDateTime endDate = to.atTime(23, 59, 59);
        return productionBatchRepository.getProductionCostReport(restaurantId, startDate, endDate);
    }

    // --- Private helpers ---

    private ProductionBatch getBatchOrThrow(Long batchId) {
        return productionBatchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Production batch not found: " + batchId));
    }

    private void validateBatchModifiable(ProductionBatch batch) {
        if (batch.getStatus() != ProductionBatch.Status.DRAFT
                && batch.getStatus() != ProductionBatch.Status.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Batch can only be modified in DRAFT or IN_PROGRESS status, current: " + batch.getStatus());
        }
    }

    private void loadRecipeInputs(ProductionBatch batch, Long productId) {
        List<ProductIngredient> recipeIngredients =
                productIngredientRepository.findByProductIdWithIngredients(productId);

        for (ProductIngredient pi : recipeIngredients) {
            if (pi.getOptional()) {
                continue;
            }

            Ingredient ingredient = pi.getIngredient();
            BigDecimal costPerUnit = ingredient.getEffectiveCost();

            ProductionBatchInput input = ProductionBatchInput.builder()
                    .productionBatch(batch)
                    .ingredient(ingredient)
                    .plannedQuantity(pi.getQuantityRequired())
                    .actualQuantity(pi.getQuantityRequired()) // default actual = planned
                    .unit(pi.getUnit() != null ? pi.getUnit() : ingredient.getUnit())
                    .costPerUnit(costPerUnit)
                    .totalCost(costPerUnit.multiply(pi.getQuantityRequired()))
                    .build();

            productionBatchInputRepository.save(input);
        }

        log.debug("Loaded {} recipe inputs for batch {}", recipeIngredients.size(), batch.getBatchNumber());
    }

    private String generateBatchNumber() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = batchSequence.incrementAndGet();
        String batchNumber = "PB-" + datePrefix + "-" + String.format("%05d", seq % 100000);

        // Ensure uniqueness
        while (productionBatchRepository.existsByBatchNumber(batchNumber)) {
            seq = batchSequence.incrementAndGet();
            batchNumber = "PB-" + datePrefix + "-" + String.format("%05d", seq % 100000);
        }

        return batchNumber;
    }
}
