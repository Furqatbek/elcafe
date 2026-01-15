package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.*;
import com.elcafe.modules.inventory.enums.TransactionType;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for calculating inventory valuation using different methods:
 * FIFO, LIFO, Weighted Average, FEFO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryValuationService {

    private final InventoryBatchRepository batchRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final ValuationSettingsRepository valuationSettingsRepository;
    private final BatchConsumptionRepository batchConsumptionRepository;
    private final InventoryTransactionRepository transactionRepository;

    /**
     * Get the current valuation method for a restaurant
     */
    @Transactional(readOnly = true)
    public ValuationMethod getValuationMethod(Long restaurantId) {
        return valuationSettingsRepository.findCurrentSettings(restaurantId, LocalDate.now())
                .map(ValuationSettings::getValuationMethod)
                .orElse(ValuationMethod.WEIGHTED_AVERAGE); // Default to WAC
    }

    /**
     * Set the valuation method for a restaurant
     */
    @Transactional
    public ValuationSettings setValuationMethod(Long restaurantId, ValuationMethod method, String createdBy) {
        log.info("Setting valuation method for restaurant {} to {}", restaurantId, method);

        // Deactivate any existing active settings
        valuationSettingsRepository.findByRestaurant_IdAndIsActiveTrue(restaurantId)
                .ifPresent(existing -> {
                    existing.deactivate(LocalDate.now().minusDays(1));
                    valuationSettingsRepository.save(existing);
                });

        // Create new settings
        ValuationSettings settings = ValuationSettings.builder()
                .restaurant(ingredientRepository.findById(restaurantId)
                        .map(Ingredient::getRestaurant)
                        .orElseThrow(() -> new RuntimeException("Restaurant not found")))
                .valuationMethod(method)
                .effectiveFrom(LocalDate.now())
                .isActive(true)
                .createdBy(createdBy)
                .build();

        return valuationSettingsRepository.save(settings);
    }

    /**
     * Calculate cost using FIFO (First-In-First-Out)
     * Consumes oldest batches first (by received date)
     */
    @Transactional
    public ConsumptionResult consumeFIFO(Long ingredientId, BigDecimal quantity, Long orderId) {
        log.debug("FIFO consumption: {} units from ingredient {}", quantity, ingredientId);

        List<InventoryBatch> batches = getActiveBatchesFIFO(ingredientId);
        return consumeFromBatches(batches, ingredientId, quantity, orderId, ValuationMethod.FIFO);
    }

    /**
     * Calculate cost using LIFO (Last-In-First-Out)
     * Consumes newest batches first (by received date)
     */
    @Transactional
    public ConsumptionResult consumeLIFO(Long ingredientId, BigDecimal quantity, Long orderId) {
        log.debug("LIFO consumption: {} units from ingredient {}", quantity, ingredientId);

        List<InventoryBatch> batches = getActiveBatchesLIFO(ingredientId);
        return consumeFromBatches(batches, ingredientId, quantity, orderId, ValuationMethod.LIFO);
    }

    /**
     * Calculate cost using Weighted Average Cost
     * Uses ingredient's WAC for all units
     */
    @Transactional
    public ConsumptionResult consumeWeightedAverage(Long ingredientId, BigDecimal quantity, Long orderId) {
        log.debug("WAC consumption: {} units from ingredient {}", quantity, ingredientId);

        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        BigDecimal wac = ingredient.getEffectiveCost();

        // Still consume from batches using FEFO for physical inventory
        List<InventoryBatch> batches = batchRepository.findActiveBatchesFEFO(ingredientId);

        List<BatchConsumption> consumptions = new ArrayList<>();
        BigDecimal remaining = quantity;
        BigDecimal totalQuantityConsumedFromBatches = BigDecimal.ZERO;

        for (InventoryBatch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal consumed = batch.consume(remaining);
            if (consumed.compareTo(BigDecimal.ZERO) > 0) {
                batchRepository.save(batch);

                // Record consumption with WAC (not batch cost)
                BatchConsumption consumption = BatchConsumption.builder()
                        .ingredient(ingredient)
                        .batch(batch)
                        .quantity(consumed)
                        .costPerUnit(wac) // Use WAC instead of batch cost
                        .totalCost(wac.multiply(consumed))
                        .valuationMethod(ValuationMethod.WEIGHTED_AVERAGE)
                        .orderId(orderId)
                        .consumedAt(LocalDateTime.now())
                        .batchNumber(batch.getBatchNumber())
                        .build();

                consumptions.add(batchConsumptionRepository.save(consumption));
                totalQuantityConsumedFromBatches = totalQuantityConsumedFromBatches.add(consumed);
                remaining = remaining.subtract(consumed);
            }
        }

        // Always deduct the full requested quantity from ingredient stock
        // regardless of whether batches exist (ingredient stock is the source of truth)
        ingredient.deductStock(quantity);
        ingredientRepository.save(ingredient);

        // If no batches were available but ingredient has stock, log a warning
        if (batches.isEmpty()) {
            log.warn("No active batches found for ingredient {} (ID: {}), deducted {} directly from ingredient stock",
                    ingredient.getName(), ingredientId, quantity);
        } else if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Could not fully consume from batches for ingredient {} (ID: {}). Requested: {}, from batches: {}, remaining: {}",
                    ingredient.getName(), ingredientId, quantity, totalQuantityConsumedFromBatches, remaining);
        }

        BigDecimal totalCost = wac.multiply(quantity);

        return new ConsumptionResult(
                quantity, // Always return the requested quantity as consumed
                totalCost,
                wac,
                ValuationMethod.WEIGHTED_AVERAGE,
                consumptions
        );
    }

    /**
     * Calculate cost using FEFO (First-Expired-First-Out)
     * Consumes batches closest to expiry first
     */
    @Transactional
    public ConsumptionResult consumeFEFO(Long ingredientId, BigDecimal quantity, Long orderId) {
        log.debug("FEFO consumption: {} units from ingredient {}", quantity, ingredientId);

        List<InventoryBatch> batches = batchRepository.findActiveBatchesFEFO(ingredientId);
        return consumeFromBatches(batches, ingredientId, quantity, orderId, ValuationMethod.FEFO);
    }

    /**
     * Consume stock using the restaurant's configured valuation method
     */
    @Transactional
    public ConsumptionResult consumeWithValuation(Long ingredientId, BigDecimal quantity, Long orderId) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        ValuationMethod method = getValuationMethod(ingredient.getRestaurant().getId());

        return switch (method) {
            case FIFO -> consumeFIFO(ingredientId, quantity, orderId);
            case LIFO -> consumeLIFO(ingredientId, quantity, orderId);
            case WEIGHTED_AVERAGE -> consumeWeightedAverage(ingredientId, quantity, orderId);
            case FEFO -> consumeFEFO(ingredientId, quantity, orderId);
        };
    }

    /**
     * Calculate total inventory value for a restaurant using specified method
     */
    @Transactional(readOnly = true)
    public InventoryValuation calculateInventoryValue(Long restaurantId, ValuationMethod method) {
        log.debug("Calculating inventory value for restaurant {} using {}", restaurantId, method);

        List<Ingredient> ingredients = ingredientRepository.findByRestaurant_Id(restaurantId);
        BigDecimal totalValue = BigDecimal.ZERO;
        List<IngredientValuation> ingredientValuations = new ArrayList<>();

        for (Ingredient ingredient : ingredients) {
            BigDecimal value = calculateIngredientValue(ingredient.getId(), method);
            totalValue = totalValue.add(value);

            ingredientValuations.add(new IngredientValuation(
                    ingredient.getId(),
                    ingredient.getName(),
                    ingredient.getCurrentStock(),
                    ingredient.getUnit(),
                    value,
                    ingredient.getEffectiveCost()
            ));
        }

        return new InventoryValuation(
                restaurantId,
                method,
                totalValue,
                ingredientValuations,
                LocalDateTime.now()
        );
    }

    /**
     * Calculate value of a specific ingredient using specified method
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateIngredientValue(Long ingredientId, ValuationMethod method) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        return switch (method) {
            case FIFO -> calculateFIFOValue(ingredientId);
            case LIFO -> calculateLIFOValue(ingredientId);
            case WEIGHTED_AVERAGE -> ingredient.getCurrentStock().multiply(ingredient.getEffectiveCost());
            case FEFO -> calculateFEFOValue(ingredientId);
        };
    }

    /**
     * Calculate FIFO ending inventory value
     * Value is based on most recent purchases (since oldest were sold first)
     */
    private BigDecimal calculateFIFOValue(Long ingredientId) {
        List<InventoryBatch> batches = getActiveBatchesLIFO(ingredientId); // Newest first for ending inventory
        return calculateBatchValue(batches);
    }

    /**
     * Calculate LIFO ending inventory value
     * Value is based on oldest purchases (since newest were sold first)
     */
    private BigDecimal calculateLIFOValue(Long ingredientId) {
        List<InventoryBatch> batches = getActiveBatchesFIFO(ingredientId); // Oldest first for ending inventory
        return calculateBatchValue(batches);
    }

    /**
     * Calculate FEFO inventory value
     */
    private BigDecimal calculateFEFOValue(Long ingredientId) {
        List<InventoryBatch> batches = batchRepository.findActiveBatchesFEFO(ingredientId);
        return calculateBatchValue(batches);
    }

    private BigDecimal calculateBatchValue(List<InventoryBatch> batches) {
        return batches.stream()
                .map(batch -> {
                    BigDecimal cost = batch.getCostPerUnit() != null ? batch.getCostPerUnit() : BigDecimal.ZERO;
                    return batch.getQuantity().multiply(cost);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Recalculate Weighted Average Cost for an ingredient
     */
    @Transactional
    public BigDecimal recalculateWAC(Long ingredientId) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        List<InventoryBatch> batches = batchRepository.findActiveBatchesFEFO(ingredientId);

        if (batches.isEmpty()) {
            return ingredient.getCostPerUnit() != null ? ingredient.getCostPerUnit() : BigDecimal.ZERO;
        }

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;

        for (InventoryBatch batch : batches) {
            BigDecimal cost = batch.getCostPerUnit() != null ? batch.getCostPerUnit() : BigDecimal.ZERO;
            totalValue = totalValue.add(batch.getQuantity().multiply(cost));
            totalQuantity = totalQuantity.add(batch.getQuantity());
        }

        if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return ingredient.getCostPerUnit() != null ? ingredient.getCostPerUnit() : BigDecimal.ZERO;
        }

        BigDecimal wac = totalValue.divide(totalQuantity, 4, RoundingMode.HALF_UP);

        ingredient.setWeightedAverageCost(wac);
        ingredient.setLastCostUpdate(LocalDateTime.now());
        ingredientRepository.save(ingredient);

        log.debug("Recalculated WAC for ingredient {}: {}", ingredient.getName(), wac);

        return wac;
    }

    /**
     * Get batches ordered by received date (oldest first) for FIFO
     */
    private List<InventoryBatch> getActiveBatchesFIFO(Long ingredientId) {
        return batchRepository.findActiveBatchesFEFO(ingredientId).stream()
                .sorted(Comparator.comparing(InventoryBatch::getReceivedDate))
                .toList();
    }

    /**
     * Get batches ordered by received date (newest first) for LIFO
     */
    private List<InventoryBatch> getActiveBatchesLIFO(Long ingredientId) {
        return batchRepository.findActiveBatchesFEFO(ingredientId).stream()
                .sorted(Comparator.comparing(InventoryBatch::getReceivedDate).reversed())
                .toList();
    }

    /**
     * Core method to consume from batches and record consumption
     */
    private ConsumptionResult consumeFromBatches(List<InventoryBatch> batches, Long ingredientId,
                                                  BigDecimal quantity, Long orderId, ValuationMethod method) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        List<BatchConsumption> consumptions = new ArrayList<>();
        BigDecimal remaining = quantity;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalQuantityConsumedFromBatches = BigDecimal.ZERO;

        for (InventoryBatch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal consumed = batch.consume(remaining);
            if (consumed.compareTo(BigDecimal.ZERO) > 0) {
                batchRepository.save(batch);

                BigDecimal batchCost = batch.getCostPerUnit() != null ? batch.getCostPerUnit() : BigDecimal.ZERO;
                BigDecimal consumedCost = batchCost.multiply(consumed);

                // Record the consumption
                BatchConsumption consumption = BatchConsumption.builder()
                        .ingredient(ingredient)
                        .batch(batch)
                        .quantity(consumed)
                        .costPerUnit(batchCost)
                        .totalCost(consumedCost)
                        .valuationMethod(method)
                        .orderId(orderId)
                        .consumedAt(LocalDateTime.now())
                        .batchNumber(batch.getBatchNumber())
                        .build();

                consumptions.add(batchConsumptionRepository.save(consumption));
                totalCost = totalCost.add(consumedCost);
                totalQuantityConsumedFromBatches = totalQuantityConsumedFromBatches.add(consumed);
                remaining = remaining.subtract(consumed);
            }
        }

        // Always deduct the full requested quantity from ingredient stock
        // regardless of whether batches exist (ingredient stock is the source of truth)
        ingredient.deductStock(quantity);
        ingredientRepository.save(ingredient);

        // Calculate average cost per unit (use effective cost if no batches consumed)
        BigDecimal avgCost;
        if (totalQuantityConsumedFromBatches.compareTo(BigDecimal.ZERO) > 0) {
            avgCost = totalCost.divide(totalQuantityConsumedFromBatches, 4, RoundingMode.HALF_UP);
            // Adjust total cost to cover full quantity if not all was from batches
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                totalCost = totalCost.add(avgCost.multiply(remaining));
            }
        } else {
            // No batches available, use ingredient's effective cost
            avgCost = ingredient.getEffectiveCost();
            totalCost = avgCost.multiply(quantity);
        }

        // Log warnings for incomplete batch consumption
        if (batches.isEmpty()) {
            log.warn("No active batches found for ingredient {} (ID: {}), deducted {} directly from ingredient stock",
                    ingredient.getName(), ingredientId, quantity);
        } else if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Could not fully consume from batches for ingredient {} (ID: {}). Requested: {}, from batches: {}, remaining: {}",
                    ingredient.getName(), ingredientId, quantity, totalQuantityConsumedFromBatches, remaining);
        }

        return new ConsumptionResult(quantity, totalCost, avgCost, method, consumptions);
    }

    /**
     * Restore inventory for a cancelled order
     * Reverses the consumption by adding quantities back to batches and ingredients.
     * Falls back to InventoryTransaction records if no BatchConsumption records exist.
     */
    @Transactional
    public void restoreInventoryForOrder(Long orderId) {
        log.info("Restoring inventory for cancelled order: {}", orderId);

        List<BatchConsumption> consumptions = batchConsumptionRepository.findByOrderId(orderId);

        if (!consumptions.isEmpty()) {
            // Restore from BatchConsumption records (batch-based tracking)
            for (BatchConsumption consumption : consumptions) {
                try {
                    // Restore quantity to batch
                    InventoryBatch batch = consumption.getBatch();
                    if (batch != null) {
                        BigDecimal currentQty = batch.getQuantity();
                        batch.setQuantity(currentQty.add(consumption.getQuantity()));

                        // Reactivate batch if it was depleted
                        if (batch.getStatus() == InventoryBatch.Status.DEPLETED) {
                            batch.setStatus(InventoryBatch.Status.ACTIVE);
                        }
                        batchRepository.save(batch);
                        log.debug("Restored {} to batch {}", consumption.getQuantity(), batch.getBatchNumber());
                    }

                    // Restore quantity to ingredient
                    Ingredient ingredient = consumption.getIngredient();
                    if (ingredient != null) {
                        ingredient.addStock(consumption.getQuantity());
                        ingredientRepository.save(ingredient);
                        log.debug("Restored {} to ingredient {}", consumption.getQuantity(), ingredient.getName());
                    }
                } catch (Exception e) {
                    log.error("Failed to restore consumption record {}: {}", consumption.getId(), e.getMessage());
                }
            }

            // Delete consumption records
            batchConsumptionRepository.deleteByOrderId(orderId);
            log.info("Restored inventory and deleted {} consumption records for order {}", consumptions.size(), orderId);
        } else {
            // Fallback: restore from InventoryTransaction records
            // This handles cases where no batches existed when order was placed
            List<InventoryTransaction> transactions = transactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId);

            if (transactions.isEmpty()) {
                log.debug("No consumption or transaction records found for order {}, nothing to restore", orderId);
                return;
            }

            for (InventoryTransaction transaction : transactions) {
                if (transaction.getType() == TransactionType.ORDER_DEDUCTION) {
                    try {
                        Ingredient ingredient = transaction.getIngredient();
                        if (ingredient != null) {
                            ingredient.addStock(transaction.getQuantity());
                            ingredientRepository.save(ingredient);
                            log.debug("Restored {} to ingredient {} from transaction record",
                                    transaction.getQuantity(), ingredient.getName());
                        }
                    } catch (Exception e) {
                        log.error("Failed to restore from transaction {}: {}", transaction.getId(), e.getMessage());
                    }
                }
            }
            log.info("Restored inventory from {} transaction records for order {}", transactions.size(), orderId);
        }
    }

    // Result classes
    public record ConsumptionResult(
            BigDecimal quantityConsumed,
            BigDecimal totalCost,
            BigDecimal averageCostPerUnit,
            ValuationMethod method,
            List<BatchConsumption> consumptions
    ) {}

    public record InventoryValuation(
            Long restaurantId,
            ValuationMethod method,
            BigDecimal totalValue,
            List<IngredientValuation> ingredientValuations,
            LocalDateTime calculatedAt
    ) {}

    public record IngredientValuation(
            Long ingredientId,
            String ingredientName,
            BigDecimal quantity,
            String unit,
            BigDecimal value,
            BigDecimal costPerUnit
    ) {}
}
