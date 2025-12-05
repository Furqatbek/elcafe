package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.InventoryTurnoverDTO;
import com.elcafe.modules.menu.entity.Ingredient;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductIngredient;
import com.elcafe.modules.menu.repository.IngredientRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for inventory analytics calculations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAnalyticsService {

    private final IngredientRepository ingredientRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    /**
     * Calculate inventory turnover ratio and related metrics
     * Inventory Turnover Ratio = Cost of Goods Sold / Average Inventory Value
     * Days to Sell Inventory = 365 / Inventory Turnover Ratio
     */
    public InventoryTurnoverDTO getInventoryTurnover(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // Get all completed orders in the period
        List<Order> orders = getCompletedOrders(startDateTime, endDateTime, restaurantId);

        // Calculate COGS (Cost of Goods Sold)
        BigDecimal totalCOGS = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(item -> {
                    Product product = productRepository.findById(item.getProductId()).orElse(null);
                    if (product != null && product.getCostPrice() != null) {
                        return product.getCostPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get all active ingredients
        List<Ingredient> allIngredients = ingredientRepository.findAll().stream()
                .filter(Ingredient::getIsActive)
                .collect(Collectors.toList());

        // Calculate average inventory value (current stock * cost per unit)
        BigDecimal totalInventoryValue = allIngredients.stream()
                .map(ingredient -> {
                    BigDecimal stock = ingredient.getCurrentStock() != null ? ingredient.getCurrentStock() : BigDecimal.ZERO;
                    BigDecimal cost = ingredient.getCostPerUnit() != null ? ingredient.getCostPerUnit() : BigDecimal.ZERO;
                    return stock.multiply(cost);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate ingredient usage during the period
        Map<Long, BigDecimal> ingredientUsage = calculateIngredientUsage(orders);

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

    private List<Order> getCompletedOrders(LocalDateTime startDateTime, LocalDateTime endDateTime, Long restaurantId) {
        if (restaurantId != null) {
            return orderRepository.findByRestaurantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    restaurantId, startDateTime, endDateTime
            ).stream()
                    .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                    .collect(Collectors.toList());
        } else {
            return orderRepository.findAll().stream()
                    .filter(order -> order.getCreatedAt().isAfter(startDateTime) && order.getCreatedAt().isBefore(endDateTime))
                    .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Calculate total ingredient usage based on products sold
     */
    private Map<Long, BigDecimal> calculateIngredientUsage(List<Order> orders) {
        Map<Long, BigDecimal> ingredientUsage = new HashMap<>();

        orders.stream()
                .flatMap(order -> order.getItems().stream())
                .forEach(item -> {
                    Product product = productRepository.findById(item.getProductId()).orElse(null);
                    if (product != null && product.getIngredients() != null) {
                        // For each ingredient in the product, add the quantity used
                        product.getIngredients().forEach(productIngredient -> {
                            Long ingredientId = productIngredient.getIngredient().getId();
                            BigDecimal quantityPerProduct = productIngredient.getQuantity() != null
                                    ? productIngredient.getQuantity()
                                    : BigDecimal.ZERO;

                            BigDecimal totalQuantityUsed = quantityPerProduct.multiply(
                                    BigDecimal.valueOf(item.getQuantity())
                            );

                            ingredientUsage.merge(
                                    ingredientId,
                                    totalQuantityUsed,
                                    BigDecimal::add
                            );
                        });
                    }
                });

        return ingredientUsage;
    }
}
