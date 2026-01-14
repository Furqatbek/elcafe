package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.IngredientCostHistory;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.enums.CostChangeReason;
import com.elcafe.modules.inventory.repository.IngredientCostHistoryRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for tracking and querying ingredient cost history
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostHistoryService {

    private final IngredientCostHistoryRepository costHistoryRepository;
    private final InventoryIngredientRepository ingredientRepository;

    /**
     * Record a cost change for an ingredient
     */
    @Transactional
    public IngredientCostHistory recordCostChange(Long ingredientId, BigDecimal newCost,
                                                   CostChangeReason reason, String createdBy) {
        return recordCostChange(ingredientId, newCost, reason, createdBy, null, null, null);
    }

    /**
     * Record a cost change from a purchase
     */
    @Transactional
    public IngredientCostHistory recordCostChangeFromPurchase(Long ingredientId, BigDecimal newCost,
                                                               InventoryBatch batch, Long purchaseOrderId,
                                                               String createdBy) {
        return recordCostChange(ingredientId, newCost, CostChangeReason.PURCHASE, createdBy,
                batch, purchaseOrderId, null);
    }

    /**
     * Record a cost change with full details
     */
    @Transactional
    public IngredientCostHistory recordCostChange(Long ingredientId, BigDecimal newCost,
                                                   CostChangeReason reason, String createdBy,
                                                   InventoryBatch batch, Long purchaseOrderId, String notes) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        BigDecimal previousCost = ingredient.getCostPerUnit() != null
                ? ingredient.getCostPerUnit()
                : BigDecimal.ZERO;

        // Close previous cost history record
        costHistoryRepository.findFirstByIngredientIdOrderByEffectiveFromDesc(ingredientId)
                .ifPresent(previous -> {
                    if (previous.getEffectiveTo() == null) {
                        previous.setEffectiveTo(LocalDateTime.now());
                        costHistoryRepository.save(previous);
                    }
                });

        // Create new cost history record
        IngredientCostHistory history = IngredientCostHistory.builder()
                .ingredient(ingredient)
                .previousCost(previousCost)
                .newCost(newCost)
                .weightedAverageCost(ingredient.getWeightedAverageCost())
                .reason(reason)
                .effectiveFrom(LocalDateTime.now())
                .batch(batch)
                .purchaseOrderId(purchaseOrderId)
                .notes(notes)
                .createdBy(createdBy)
                .build();

        IngredientCostHistory saved = costHistoryRepository.save(history);

        // Update ingredient's cost per unit
        ingredient.setCostPerUnit(newCost);
        ingredient.setLastCostUpdate(LocalDateTime.now());
        ingredientRepository.save(ingredient);

        log.info("Recorded cost change for ingredient {}: {} -> {} ({})",
                ingredient.getName(), previousCost, newCost, reason);

        return saved;
    }

    /**
     * Get cost history for an ingredient
     */
    @Transactional(readOnly = true)
    public List<IngredientCostHistory> getCostHistory(Long ingredientId) {
        return costHistoryRepository.findByIngredientIdOrderByEffectiveFromDesc(ingredientId);
    }

    /**
     * Get paginated cost history
     */
    @Transactional(readOnly = true)
    public Page<IngredientCostHistory> getCostHistoryPaginated(Long ingredientId, int page, int size) {
        return costHistoryRepository.findByIngredientId(ingredientId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "effectiveFrom")));
    }

    /**
     * Get cost at a specific point in time
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getCostAtDateTime(Long ingredientId, LocalDateTime dateTime) {
        List<IngredientCostHistory> history = costHistoryRepository.findCostAtDateTime(ingredientId, dateTime);

        if (history.isEmpty()) {
            // No history found, return current cost
            return ingredientRepository.findById(ingredientId)
                    .map(Ingredient::getCostPerUnit);
        }

        return Optional.of(history.get(0).getNewCost());
    }

    /**
     * Get cost changes in a date range
     */
    @Transactional(readOnly = true)
    public List<IngredientCostHistory> getCostChangesInRange(Long ingredientId,
                                                              LocalDateTime startDate,
                                                              LocalDateTime endDate) {
        return costHistoryRepository.findByIngredientIdAndDateRange(ingredientId, startDate, endDate);
    }

    /**
     * Get all cost changes for a restaurant in a date range
     */
    @Transactional(readOnly = true)
    public List<IngredientCostHistory> getRestaurantCostChanges(Long restaurantId,
                                                                 LocalDateTime startDate,
                                                                 LocalDateTime endDate) {
        return costHistoryRepository.findByRestaurant_IdAndDateRange(restaurantId, startDate, endDate);
    }

    /**
     * Get cost changes by reason
     */
    @Transactional(readOnly = true)
    public List<IngredientCostHistory> getCostChangesByReason(Long ingredientId, CostChangeReason reason) {
        return costHistoryRepository.findByIngredientIdAndReasonOrderByEffectiveFromDesc(ingredientId, reason);
    }

    /**
     * Get average cost over a period
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getAverageCostInPeriod(Long ingredientId,
                                                        LocalDateTime startDate,
                                                        LocalDateTime endDate) {
        return costHistoryRepository.getAverageCostInPeriod(ingredientId, startDate, endDate);
    }

    /**
     * Get the most recent cost change
     */
    @Transactional(readOnly = true)
    public Optional<IngredientCostHistory> getMostRecentCostChange(Long ingredientId) {
        return costHistoryRepository.findFirstByIngredientIdOrderByEffectiveFromDesc(ingredientId);
    }

    /**
     * Calculate cost variance (current vs historical average)
     */
    @Transactional(readOnly = true)
    public CostVariance calculateCostVariance(Long ingredientId, LocalDateTime startDate, LocalDateTime endDate) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        BigDecimal currentCost = ingredient.getCostPerUnit() != null
                ? ingredient.getCostPerUnit()
                : BigDecimal.ZERO;

        Optional<BigDecimal> avgCost = getAverageCostInPeriod(ingredientId, startDate, endDate);

        if (avgCost.isEmpty() || avgCost.get().compareTo(BigDecimal.ZERO) == 0) {
            return new CostVariance(ingredientId, ingredient.getName(), currentCost,
                    currentCost, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal variance = currentCost.subtract(avgCost.get());
        BigDecimal variancePercent = variance.divide(avgCost.get(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return new CostVariance(ingredientId, ingredient.getName(), currentCost,
                avgCost.get(), variance, variancePercent);
    }

    /**
     * Count cost changes for an ingredient
     */
    @Transactional(readOnly = true)
    public long countCostChanges(Long ingredientId) {
        return costHistoryRepository.countByIngredientId(ingredientId);
    }

    // Result classes
    public record CostVariance(
            Long ingredientId,
            String ingredientName,
            BigDecimal currentCost,
            BigDecimal averageCost,
            BigDecimal variance,
            BigDecimal variancePercent
    ) {}
}
