package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.InventoryTurnoverDTO;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.service.BatchConsumptionService;
import com.elcafe.modules.inventory.service.InventoryValuationService;
import com.elcafe.modules.menu.entity.Ingredient;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductIngredient;
import com.elcafe.modules.menu.repository.IngredientRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.dto.ProductSalesRow;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for inventory analytics calculations.
 * Uses shift-based time ranges for consistent reporting across midnight-crossing shifts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAnalyticsService {

    private final IngredientRepository ingredientRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final InventoryValuationService valuationService;
    private final BatchConsumptionService batchConsumptionService;
    private final ShiftTimeService shiftTimeService;

    /**
     * Calculate inventory turnover ratio and related metrics.
     * Inventory Turnover Ratio = Cost of Goods Sold / Average Inventory Value
     * Days to Sell Inventory = 365 / Inventory Turnover Ratio
     *
     * Now uses actual batch consumption COGS and valuation method for inventory value.
     * Uses shift-based time ranges for restaurants with midnight-crossing shifts.
     */
    public InventoryTurnoverDTO getInventoryTurnover(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        // Get shift-based time range
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, startDate, endDate);
        log.debug("Inventory turnover using shift range: {} to {}", shift.start(), shift.end());

        // Per-product quantity sums over revenue-status orders, aggregated in the DB (audit E3) — no
        // order/item entity graphs are materialized.
        List<ProductSalesRow> productSales = orderRepository.sumProductSalesByStatus(
                restaurantId, shift.start(), shift.end(), ShiftTimeService.REVENUE_STATUSES);

        // Try to get COGS from batch consumption data first
        BigDecimal totalCOGS = BigDecimal.ZERO;
        if (restaurantId != null) {
            try {
                totalCOGS = batchConsumptionService.calculateTotalCOGS(restaurantId, shift.start().toLocalDateTime(), shift.end().toLocalDateTime());
            } catch (Exception e) {
                log.warn("Could not get batch-based COGS for restaurant {}, falling back to product costs: {}",
                         restaurantId, e.getMessage());
            }
        }

        // Batch-load the products once (the old fallback did a findById per order item — an N+1 on top
        // of the full-range load). The aggregate already excludes bundle/packaging rows (null productId).
        Map<Long, Product> productsById = productRepository.findAllById(
                        productSales.stream().map(ProductSalesRow::productId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getId, p -> p));

        // Fall back to product-based COGS if no batch data
        if (totalCOGS.compareTo(BigDecimal.ZERO) == 0) {
            totalCOGS = productSales.stream()
                    .map(row -> {
                        Product product = productsById.get(row.productId());
                        if (product != null && product.getCostPrice() != null) {
                            return product.getCostPrice().multiply(BigDecimal.valueOf(row.quantitySold()));
                        }
                        return BigDecimal.ZERO;
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Get all active ingredients using proper query
        List<Ingredient> allIngredients = ingredientRepository.findByIsActiveTrue();

        // Try to get inventory value using valuation service
        BigDecimal totalInventoryValue = BigDecimal.ZERO;
        if (restaurantId != null) {
            try {
                ValuationMethod method = valuationService.getValuationMethod(restaurantId);
                InventoryValuationService.InventoryValuation valuation =
                        valuationService.calculateInventoryValue(restaurantId, method);
                totalInventoryValue = valuation.totalValue();
            } catch (Exception e) {
                log.warn("Could not get valuation-based inventory value for restaurant {}, falling back to simple calculation: {}",
                         restaurantId, e.getMessage());
            }
        }

        // Fall back to simple calculation if valuation service fails
        if (totalInventoryValue.compareTo(BigDecimal.ZERO) == 0) {
            totalInventoryValue = allIngredients.stream()
                    .map(ingredient -> {
                        BigDecimal stock = ingredient.getCurrentStock() != null ? ingredient.getCurrentStock() : BigDecimal.ZERO;
                        BigDecimal cost = ingredient.getCostPerUnit() != null ? ingredient.getCostPerUnit() : BigDecimal.ZERO;
                        return stock.multiply(cost);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Calculate ingredient usage during the period
        Map<Long, BigDecimal> ingredientUsage = calculateIngredientUsage(productSales, productsById);

        // Calculate turnover metrics for each ingredient
        List<InventoryTurnoverDTO.IngredientTurnoverDTO> ingredientTurnovers = allIngredients.stream()
                .map(ingredient -> {
                    BigDecimal quantityUsed = ingredientUsage.getOrDefault(ingredient.getId(), BigDecimal.ZERO);
                    BigDecimal averageStock = ingredient.getCurrentStock() != null
                            ? ingredient.getCurrentStock()
                            : BigDecimal.ZERO;

                    // Turnover ratio = Quantity Used / Average Stock
                    double turnoverRatio = averageStock.compareTo(BigDecimal.ZERO) > 0
                            ? quantityUsed.divide(averageStock, 4, RoundingMode.HALF_UP).doubleValue()
                            : 0.0;

                    // Adjust for period length
                    long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
                    double annualizedTurnoverRatio = turnoverRatio * (365.0 / daysBetween);

                    // Days to sell inventory
                    int daysToSell = annualizedTurnoverRatio > 0
                            ? (int) (365 / annualizedTurnoverRatio)
                            : 0;

                    // Cost value
                    BigDecimal costPerUnit = ingredient.getCostPerUnit() != null
                            ? ingredient.getCostPerUnit()
                            : BigDecimal.ZERO;
                    BigDecimal costValue = quantityUsed.multiply(costPerUnit);

                    return InventoryTurnoverDTO.IngredientTurnoverDTO.builder()
                            .ingredientId(ingredient.getId())
                            .ingredientName(ingredient.getName())
                            .category(ingredient.getCategory() != null ? ingredient.getCategory().name() : "UNKNOWN")
                            .quantityUsed(quantityUsed)
                            .averageStock(averageStock)
                            .turnoverRatio(annualizedTurnoverRatio)
                            .daysToSellInventory(daysToSell)
                            .costValue(costValue)
                            .build();
                })
                .sorted(Comparator.comparing(InventoryTurnoverDTO.IngredientTurnoverDTO::getTurnoverRatio).reversed())
                .collect(Collectors.toList());

        // Overall turnover ratio
        double overallTurnoverRatio = totalInventoryValue.compareTo(BigDecimal.ZERO) > 0
                ? totalCOGS.divide(totalInventoryValue, 4, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        // Adjust for period length
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        double annualizedOverallTurnover = overallTurnoverRatio * (365.0 / daysBetween);

        // Days to sell inventory
        double avgDaysToSell = annualizedOverallTurnover > 0
                ? 365.0 / annualizedOverallTurnover
                : 0.0;

        return InventoryTurnoverDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .overallTurnoverRatio(annualizedOverallTurnover)
                .averageDaysToSellInventory(avgDaysToSell)
                .costOfGoodsSold(totalCOGS)
                .averageInventoryValue(totalInventoryValue)
                .ingredientTurnovers(ingredientTurnovers)
                .build();
    }

    // Helper methods

    /**
     * Calculate total ingredient usage based on products sold. Works on per-product quantity sums
     * (summing per product first is algebraically identical to the old per-item merge).
     */
    private Map<Long, BigDecimal> calculateIngredientUsage(List<ProductSalesRow> productSales,
                                                           Map<Long, Product> productsById) {
        Map<Long, BigDecimal> ingredientUsage = new HashMap<>();

        for (ProductSalesRow row : productSales) {
            Product product = productsById.get(row.productId());
            if (product != null && product.getIngredients() != null) {
                // For each ingredient in the product, add the quantity used
                product.getIngredients().forEach(productIngredient -> {
                    Long ingredientId = productIngredient.getIngredient().getId();
                    BigDecimal quantityPerProduct = productIngredient.getQuantity() != null
                            ? productIngredient.getQuantity()
                            : BigDecimal.ZERO;

                    BigDecimal totalQuantityUsed = quantityPerProduct.multiply(
                            BigDecimal.valueOf(row.quantitySold())
                    );

                    ingredientUsage.merge(
                            ingredientId,
                            totalQuantityUsed,
                            BigDecimal::add
                    );
                });
            }
        }

        return ingredientUsage;
    }
}
