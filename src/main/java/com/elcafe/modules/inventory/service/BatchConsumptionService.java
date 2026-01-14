package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.BatchConsumption;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.BatchConsumptionRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing and querying batch consumption records
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchConsumptionService {

    private final BatchConsumptionRepository consumptionRepository;
    private final InventoryIngredientRepository ingredientRepository;

    /**
     * Record a batch consumption
     */
    @Transactional
    public BatchConsumption recordConsumption(InventoryBatch batch, BigDecimal quantity,
                                               ValuationMethod method, Long orderId, Long orderItemId) {
        Ingredient ingredient = batch.getIngredient();
        BigDecimal cost = batch.getCostPerUnit() != null ? batch.getCostPerUnit() : BigDecimal.ZERO;

        BatchConsumption consumption = BatchConsumption.builder()
                .ingredient(ingredient)
                .batch(batch)
                .quantity(quantity)
                .costPerUnit(cost)
                .totalCost(cost.multiply(quantity))
                .valuationMethod(method)
                .orderId(orderId)
                .orderItemId(orderItemId)
                .consumedAt(LocalDateTime.now())
                .batchNumber(batch.getBatchNumber())
                .build();

        BatchConsumption saved = consumptionRepository.save(consumption);
        log.debug("Recorded consumption: {} units from batch {} at cost {}",
                quantity, batch.getBatchNumber(), cost);

        return saved;
    }

    /**
     * Get all consumptions for an order
     */
    @Transactional(readOnly = true)
    public List<BatchConsumption> getConsumptionsForOrder(Long orderId) {
        return consumptionRepository.findByOrderId(orderId);
    }

    /**
     * Calculate COGS for an order
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateOrderCOGS(Long orderId) {
        return consumptionRepository.calculateOrderCOGS(orderId);
    }

    /**
     * Calculate total COGS for a restaurant in a date range
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalCOGS(Long restaurantId, LocalDateTime startDate, LocalDateTime endDate) {
        return consumptionRepository.calculateTotalCOGS(restaurantId, startDate, endDate);
    }

    /**
     * Get consumption history for an ingredient
     */
    @Transactional(readOnly = true)
    public List<BatchConsumption> getConsumptionHistory(Long ingredientId) {
        return consumptionRepository.findByIngredientIdOrderByConsumedAtDesc(ingredientId);
    }

    /**
     * Get paginated consumption history
     */
    @Transactional(readOnly = true)
    public Page<BatchConsumption> getConsumptionHistoryPaginated(Long ingredientId, int page, int size) {
        return consumptionRepository.findByIngredientId(ingredientId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "consumedAt")));
    }

    /**
     * Get consumption by ingredient in a date range
     */
    @Transactional(readOnly = true)
    public List<BatchConsumption> getConsumptionInRange(Long ingredientId,
                                                         LocalDateTime startDate,
                                                         LocalDateTime endDate) {
        return consumptionRepository.findByIngredientIdAndDateRange(ingredientId, startDate, endDate);
    }

    /**
     * Get consumption summary by ingredient for a restaurant
     */
    @Transactional(readOnly = true)
    public List<IngredientConsumptionSummary> getConsumptionByIngredient(Long restaurantId,
                                                                          LocalDateTime startDate,
                                                                          LocalDateTime endDate) {
        List<Object[]> results = consumptionRepository.getConsumptionByIngredient(
                restaurantId, startDate, endDate);

        return results.stream()
                .map(row -> new IngredientConsumptionSummary(
                        (Long) row[0],
                        (String) row[1],
                        (BigDecimal) row[2],
                        (BigDecimal) row[3]
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get total quantity consumed for an ingredient
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalQuantityConsumed(Long ingredientId) {
        return consumptionRepository.getTotalQuantityConsumed(ingredientId);
    }

    /**
     * Get quantity consumed in a period
     */
    @Transactional(readOnly = true)
    public BigDecimal getQuantityConsumedInPeriod(Long ingredientId,
                                                   LocalDateTime startDate,
                                                   LocalDateTime endDate) {
        return consumptionRepository.getQuantityConsumedInPeriod(ingredientId, startDate, endDate);
    }

    /**
     * Get average consumption cost for an ingredient
     */
    @Transactional(readOnly = true)
    public BigDecimal getAverageConsumptionCost(Long ingredientId) {
        return consumptionRepository.getAverageConsumptionCost(ingredientId);
    }

    /**
     * Get consumption statistics for an ingredient
     */
    @Transactional(readOnly = true)
    public ConsumptionStats getConsumptionStats(Long ingredientId,
                                                 LocalDateTime startDate,
                                                 LocalDateTime endDate) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        List<BatchConsumption> consumptions = getConsumptionInRange(ingredientId, startDate, endDate);

        if (consumptions.isEmpty()) {
            return new ConsumptionStats(ingredientId, ingredient.getName(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }

        BigDecimal totalQuantity = consumptions.stream()
                .map(BatchConsumption::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCost = consumptions.stream()
                .map(BatchConsumption::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgCost = totalQuantity.compareTo(BigDecimal.ZERO) > 0
                ? totalCost.divide(totalQuantity, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal minCost = consumptions.stream()
                .map(BatchConsumption::getCostPerUnit)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxCost = consumptions.stream()
                .map(BatchConsumption::getCostPerUnit)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return new ConsumptionStats(ingredientId, ingredient.getName(),
                totalQuantity, totalCost, avgCost, minCost, maxCost, consumptions.size());
    }

    /**
     * Delete consumptions for a cancelled order
     */
    @Transactional
    public void deleteConsumptionsForOrder(Long orderId) {
        long count = consumptionRepository.countByOrderId(orderId);
        if (count > 0) {
            consumptionRepository.deleteByOrderId(orderId);
            log.info("Deleted {} consumption records for cancelled order {}", count, orderId);
        }
    }

    /**
     * Get consumptions for a batch
     */
    @Transactional(readOnly = true)
    public List<BatchConsumption> getConsumptionsForBatch(Long batchId) {
        return consumptionRepository.findByBatchIdOrderByConsumedAtDesc(batchId);
    }

    // Result classes
    public record IngredientConsumptionSummary(
            Long ingredientId,
            String ingredientName,
            BigDecimal totalQuantity,
            BigDecimal totalCost
    ) {}

    public record ConsumptionStats(
            Long ingredientId,
            String ingredientName,
            BigDecimal totalQuantity,
            BigDecimal totalCost,
            BigDecimal averageCostPerUnit,
            BigDecimal minCostPerUnit,
            BigDecimal maxCostPerUnit,
            int transactionCount
    ) {}
}
