package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.dto.ValuationReportDTO.*;
import com.elcafe.modules.inventory.entity.BatchConsumption;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.BatchConsumptionRepository;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating valuation and cost variance reports
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValuationReportService {

    private final InventoryValuationService valuationService;
    private final InventoryIngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final BatchConsumptionRepository consumptionRepository;

    /**
     * Generate comprehensive valuation comparison report
     */
    @Transactional(readOnly = true)
    public ValuationComparisonReport generateComparisonReport(Long restaurantId) {
        log.info("Generating valuation comparison report for restaurant {}", restaurantId);

        List<Ingredient> ingredients = ingredientRepository.findByRestaurantId(restaurantId);
        ValuationMethod currentMethod = valuationService.getValuationMethod(restaurantId);

        // Calculate total values by method
        BigDecimal fifoTotal = BigDecimal.ZERO;
        BigDecimal lifoTotal = BigDecimal.ZERO;
        BigDecimal wacTotal = BigDecimal.ZERO;

        List<IngredientValuationComparison> comparisons = new ArrayList<>();

        for (Ingredient ingredient : ingredients) {
            if (ingredient.getCurrentStock() == null ||
                ingredient.getCurrentStock().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal fifoValue = valuationService.calculateIngredientValue(ingredient.getId(), ValuationMethod.FIFO);
            BigDecimal lifoValue = valuationService.calculateIngredientValue(ingredient.getId(), ValuationMethod.LIFO);
            BigDecimal wacValue = valuationService.calculateIngredientValue(ingredient.getId(), ValuationMethod.WEIGHTED_AVERAGE);

            fifoTotal = fifoTotal.add(fifoValue);
            lifoTotal = lifoTotal.add(lifoValue);
            wacTotal = wacTotal.add(wacValue);

            // Calculate max variance
            BigDecimal maxValue = fifoValue.max(lifoValue).max(wacValue);
            BigDecimal minValue = fifoValue.min(lifoValue).min(wacValue);
            BigDecimal maxVariance = maxValue.subtract(minValue);
            BigDecimal variancePercent = maxValue.compareTo(BigDecimal.ZERO) > 0
                    ? maxVariance.divide(maxValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            comparisons.add(IngredientValuationComparison.builder()
                    .ingredientId(ingredient.getId())
                    .ingredientName(ingredient.getName())
                    .category(ingredient.getCategory() != null ? ingredient.getCategory().name() : "UNKNOWN")
                    .currentStock(ingredient.getCurrentStock())
                    .unit(ingredient.getUnit())
                    .fifoValue(fifoValue)
                    .lifoValue(lifoValue)
                    .wacValue(wacValue)
                    .maxVariance(maxVariance)
                    .variancePercent(variancePercent)
                    .build());
        }

        // Sort by variance percent descending
        comparisons.sort(Comparator.comparing(IngredientValuationComparison::getVariancePercent).reversed());

        // Generate recommendation
        String recommendation = generateRecommendation(fifoTotal, lifoTotal, wacTotal);

        // Get current method value
        BigDecimal currentMethodValue = switch (currentMethod) {
            case FIFO -> fifoTotal;
            case LIFO -> lifoTotal;
            case WEIGHTED_AVERAGE -> wacTotal;
            case FEFO -> fifoTotal; // FEFO uses same values as FIFO for valuation
        };

        return ValuationComparisonReport.builder()
                .restaurantId(restaurantId)
                .generatedAt(LocalDateTime.now())
                .fifoValue(fifoTotal)
                .lifoValue(lifoTotal)
                .weightedAverageValue(wacTotal)
                .fifoVsLifo(fifoTotal.subtract(lifoTotal))
                .fifoVsWac(fifoTotal.subtract(wacTotal))
                .lifoVsWac(lifoTotal.subtract(wacTotal))
                .currentMethod(currentMethod)
                .currentMethodValue(currentMethodValue)
                .recommendation(recommendation)
                .ingredientComparisons(comparisons)
                .build();
    }

    /**
     * Generate full inventory valuation report
     */
    @Transactional(readOnly = true)
    public InventoryValuationReport generateValuationReport(Long restaurantId, ValuationMethod method) {
        log.info("Generating valuation report for restaurant {} using method {}", restaurantId, method);

        ValuationMethod useMethod = method != null ? method : valuationService.getValuationMethod(restaurantId);
        List<Ingredient> ingredients = ingredientRepository.findByRestaurantId(restaurantId);

        BigDecimal totalValue = BigDecimal.ZERO;
        int ingredientsWithStock = 0;
        BigDecimal totalQuantity = BigDecimal.ZERO;

        Map<String, List<IngredientValuationDetail>> byCategory = new HashMap<>();
        List<IngredientValuationDetail> allValuations = new ArrayList<>();

        for (Ingredient ingredient : ingredients) {
            BigDecimal value = valuationService.calculateIngredientValue(ingredient.getId(), useMethod);
            BigDecimal stock = ingredient.getCurrentStock() != null ? ingredient.getCurrentStock() : BigDecimal.ZERO;

            if (stock.compareTo(BigDecimal.ZERO) > 0) {
                ingredientsWithStock++;
                totalQuantity = totalQuantity.add(stock);
            }

            totalValue = totalValue.add(value);

            // Get batch info
            List<InventoryBatch> batches = batchRepository.findActiveBatchesFEFO(ingredient.getId());
            BigDecimal oldestCost = batches.isEmpty() ? BigDecimal.ZERO
                    : batches.stream()
                        .min(Comparator.comparing(InventoryBatch::getReceivedDate))
                        .map(b -> b.getCostPerUnit() != null ? b.getCostPerUnit() : BigDecimal.ZERO)
                        .orElse(BigDecimal.ZERO);
            BigDecimal newestCost = batches.isEmpty() ? BigDecimal.ZERO
                    : batches.stream()
                        .max(Comparator.comparing(InventoryBatch::getReceivedDate))
                        .map(b -> b.getCostPerUnit() != null ? b.getCostPerUnit() : BigDecimal.ZERO)
                        .orElse(BigDecimal.ZERO);

            String category = ingredient.getCategory() != null ? ingredient.getCategory().name() : "UNKNOWN";

            IngredientValuationDetail detail = IngredientValuationDetail.builder()
                    .ingredientId(ingredient.getId())
                    .ingredientName(ingredient.getName())
                    .category(category)
                    .currentStock(stock)
                    .unit(ingredient.getUnit())
                    .costPerUnit(ingredient.getEffectiveCost())
                    .totalValue(value)
                    .activeBatches(batches.size())
                    .oldestBatchCost(oldestCost)
                    .newestBatchCost(newestCost)
                    .build();

            allValuations.add(detail);
            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(detail);
        }

        // Calculate percentages
        for (IngredientValuationDetail detail : allValuations) {
            BigDecimal pct = totalValue.compareTo(BigDecimal.ZERO) > 0
                    ? detail.getTotalValue().divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            detail.setPercentageOfTotal(pct);
        }

        // Build category valuations
        List<CategoryValuation> categoryValuations = byCategory.entrySet().stream()
                .map(entry -> {
                    BigDecimal catTotal = entry.getValue().stream()
                            .map(IngredientValuationDetail::getTotalValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal pct = totalValue.compareTo(BigDecimal.ZERO) > 0
                            ? catTotal.divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;
                    return CategoryValuation.builder()
                            .category(entry.getKey())
                            .ingredientCount(entry.getValue().size())
                            .totalValue(catTotal)
                            .percentageOfTotal(pct)
                            .build();
                })
                .sorted(Comparator.comparing(CategoryValuation::getTotalValue).reversed())
                .collect(Collectors.toList());

        // Sort all valuations by value
        allValuations.sort(Comparator.comparing(IngredientValuationDetail::getTotalValue).reversed());

        BigDecimal avgCost = totalQuantity.compareTo(BigDecimal.ZERO) > 0
                ? totalValue.divide(totalQuantity, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return InventoryValuationReport.builder()
                .restaurantId(restaurantId)
                .method(useMethod)
                .generatedAt(LocalDateTime.now())
                .totalInventoryValue(totalValue)
                .totalIngredients(ingredients.size())
                .ingredientsWithStock(ingredientsWithStock)
                .averageCostPerUnit(avgCost)
                .categoryValuations(categoryValuations)
                .ingredientValuations(allValuations)
                .build();
    }

    /**
     * Generate cost variance report (Actual vs Standard)
     */
    @Transactional(readOnly = true)
    public CostVarianceReport generateCostVarianceReport(Long restaurantId,
                                                          LocalDateTime startDate,
                                                          LocalDateTime endDate) {
        log.info("Generating cost variance report for restaurant {} from {} to {}",
                restaurantId, startDate, endDate);

        // Get all consumption records in the period
        List<BatchConsumption> consumptions = consumptionRepository
                .findByRestaurantAndDateRange(restaurantId, startDate, endDate);

        // Group by ingredient
        Map<Long, List<BatchConsumption>> byIngredient = consumptions.stream()
                .collect(Collectors.groupingBy(c -> c.getIngredient().getId()));

        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalStandard = BigDecimal.ZERO;
        int favorable = 0;
        int unfavorable = 0;
        BigDecimal totalFavorableAmt = BigDecimal.ZERO;
        BigDecimal totalUnfavorableAmt = BigDecimal.ZERO;

        List<IngredientCostVariance> variances = new ArrayList<>();

        for (Map.Entry<Long, List<BatchConsumption>> entry : byIngredient.entrySet()) {
            Long ingredientId = entry.getKey();
            List<BatchConsumption> ingredientConsumptions = entry.getValue();

            Ingredient ingredient = ingredientRepository.findById(ingredientId).orElse(null);
            if (ingredient == null) continue;

            // Actual cost from consumption records
            BigDecimal actualTotal = ingredientConsumptions.stream()
                    .map(c -> c.getTotalCost() != null ? c.getTotalCost() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalQty = ingredientConsumptions.stream()
                    .map(BatchConsumption::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal actualPerUnit = totalQty.compareTo(BigDecimal.ZERO) > 0
                    ? actualTotal.divide(totalQty, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // Standard cost (costPerUnit on ingredient - this is the baseline/standard)
            BigDecimal standardPerUnit = ingredient.getCostPerUnit() != null
                    ? ingredient.getCostPerUnit()
                    : BigDecimal.ZERO;

            BigDecimal standardTotal = standardPerUnit.multiply(totalQty);

            // Variance (favorable if actual < standard)
            BigDecimal varianceAmt = actualTotal.subtract(standardTotal);
            boolean isFavorable = varianceAmt.compareTo(BigDecimal.ZERO) < 0;

            BigDecimal variancePct = standardTotal.compareTo(BigDecimal.ZERO) > 0
                    ? varianceAmt.abs().divide(standardTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            // Determine reason
            String reason = determineVarianceReason(actualPerUnit, standardPerUnit, isFavorable);

            totalActual = totalActual.add(actualTotal);
            totalStandard = totalStandard.add(standardTotal);

            if (isFavorable) {
                favorable++;
                totalFavorableAmt = totalFavorableAmt.add(varianceAmt.abs());
            } else if (varianceAmt.compareTo(BigDecimal.ZERO) > 0) {
                unfavorable++;
                totalUnfavorableAmt = totalUnfavorableAmt.add(varianceAmt);
            }

            variances.add(IngredientCostVariance.builder()
                    .ingredientId(ingredientId)
                    .ingredientName(ingredient.getName())
                    .category(ingredient.getCategory() != null ? ingredient.getCategory().name() : "UNKNOWN")
                    .quantityConsumed(totalQty)
                    .unit(ingredient.getUnit())
                    .actualCostPerUnit(actualPerUnit)
                    .standardCostPerUnit(standardPerUnit)
                    .actualTotalCost(actualTotal)
                    .standardTotalCost(standardTotal)
                    .varianceAmount(varianceAmt)
                    .variancePercent(variancePct)
                    .favorable(isFavorable)
                    .varianceReason(reason)
                    .build());
        }

        // Sort by variance amount descending (unfavorable first)
        variances.sort(Comparator.comparing(IngredientCostVariance::getVarianceAmount).reversed());

        BigDecimal totalVariance = totalActual.subtract(totalStandard);
        BigDecimal totalVariancePct = totalStandard.compareTo(BigDecimal.ZERO) > 0
                ? totalVariance.divide(totalStandard, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return CostVarianceReport.builder()
                .restaurantId(restaurantId)
                .periodStart(startDate)
                .periodEnd(endDate)
                .generatedAt(LocalDateTime.now())
                .totalActualCost(totalActual)
                .totalStandardCost(totalStandard)
                .totalVariance(totalVariance)
                .variancePercent(totalVariancePct)
                .favorableVariances(favorable)
                .unfavorableVariances(unfavorable)
                .totalFavorable(totalFavorableAmt)
                .totalUnfavorable(totalUnfavorableAmt)
                .ingredientVariances(variances)
                .build();
    }

    private String generateRecommendation(BigDecimal fifo, BigDecimal lifo, BigDecimal wac) {
        if (fifo.compareTo(lifo) > 0 && fifo.compareTo(wac) > 0) {
            return "FIFO shows highest inventory value. During rising prices, FIFO results in lower COGS and higher profits. Consider LIFO if you want to minimize taxable income.";
        } else if (lifo.compareTo(fifo) > 0 && lifo.compareTo(wac) > 0) {
            return "LIFO shows highest inventory value. This is unusual and suggests falling prices. LIFO may minimize tax during inflation.";
        } else {
            return "WAC provides stable valuation that smooths price fluctuations. Recommended for consistent financial reporting.";
        }
    }

    private String determineVarianceReason(BigDecimal actual, BigDecimal standard, boolean favorable) {
        BigDecimal diff = actual.subtract(standard).abs();
        BigDecimal pct = standard.compareTo(BigDecimal.ZERO) > 0
                ? diff.divide(standard, 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        if (pct.compareTo(BigDecimal.valueOf(10)) > 0) {
            if (favorable) {
                return "Significant cost reduction - possible supplier discount or market price drop";
            } else {
                return "Significant cost increase - review supplier pricing or market conditions";
            }
        } else if (pct.compareTo(BigDecimal.valueOf(5)) > 0) {
            if (favorable) {
                return "Moderate cost reduction - likely market fluctuation";
            } else {
                return "Moderate cost increase - monitor trend";
            }
        } else {
            return "Within normal variance range";
        }
    }
}
