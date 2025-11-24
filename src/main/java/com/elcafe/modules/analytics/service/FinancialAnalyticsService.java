package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.*;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for financial analytics calculations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialAnalyticsService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /**
     * Calculate daily revenue for a date range
     */
    public List<DailyRevenueDTO> getDailyRevenue(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Order> orders = getCompletedOrders(startDateTime, endDateTime, restaurantId);

        Map<LocalDate, List<Order>> ordersByDate = orders.stream()
                .collect(Collectors.groupingBy(order -> order.getCreatedAt().toLocalDate()));

        return ordersByDate.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<Order> dailyOrders = entry.getValue();

                    BigDecimal totalRevenue = dailyOrders.stream()
                            .map(Order::getTotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    int totalOrders = dailyOrders.size();

                    BigDecimal avgOrderValue = totalOrders > 0
                            ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    // Revenue by payment method
                    BigDecimal cashRevenue = calculateRevenueByPaymentMethod(dailyOrders, PaymentMethod.CASH);
                    BigDecimal cardRevenue = calculateRevenueByPaymentMethod(dailyOrders, PaymentMethod.CARD);
                    BigDecimal onlineRevenue = calculateRevenueByPaymentMethod(dailyOrders, PaymentMethod.ONLINE)
                            .add(calculateRevenueByPaymentMethod(dailyOrders, PaymentMethod.WALLET));

                    return DailyRevenueDTO.builder()
                            .date(date)
                            .totalRevenue(totalRevenue)
                            .totalOrders(totalOrders)
                            .averageOrderValue(avgOrderValue)
                            .cashRevenue(cashRevenue)
                            .cardRevenue(cardRevenue)
                            .onlineRevenue(onlineRevenue)
                            .build();
                })
                .sorted(Comparator.comparing(DailyRevenueDTO::getDate))
                .collect(Collectors.toList());
    }

    /**
     * Calculate sales per category
     */
    public List<SalesPerCategoryDTO> getSalesPerCategory(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Order> orders = getCompletedOrders(startDateTime, endDateTime, restaurantId);

        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group order items by product and category
        Map<Long, List<OrderItem>> itemsByProduct = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .collect(Collectors.groupingBy(OrderItem::getProductId));

        // Get all products
        Set<Long> productIds = itemsByProduct.keySet();
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Group by category
        Map<Long, List<OrderItem>> itemsByCategory = new HashMap<>();
        itemsByProduct.forEach((productId, items) -> {
            Product product = products.get(productId);
            if (product != null && product.getCategory() != null) {
                Long categoryId = product.getCategory().getId();
                itemsByCategory.computeIfAbsent(categoryId, k -> new ArrayList<>()).addAll(items);
            }
        });

        return itemsByCategory.entrySet().stream()
                .map(entry -> {
                    Long categoryId = entry.getKey();
                    List<OrderItem> categoryItems = entry.getValue();

                    // Get category name from first product
                    String categoryName = categoryItems.stream()
                            .map(item -> products.get(item.getProductId()))
                            .filter(Objects::nonNull)
                            .findFirst()
                            .map(p -> p.getCategory().getName())
                            .orElse("Unknown");

                    BigDecimal categoryRevenue = categoryItems.stream()
                            .map(OrderItem::getTotalPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    int totalItemsSold = categoryItems.stream()
                            .mapToInt(OrderItem::getQuantity)
                            .sum();

                    BigDecimal percentageOfTotal = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                            ? categoryRevenue.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    BigDecimal avgItemPrice = totalItemsSold > 0
                            ? categoryRevenue.divide(BigDecimal.valueOf(totalItemsSold), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    long numberOfProducts = categoryItems.stream()
                            .map(OrderItem::getProductId)
                            .distinct()
                            .count();

                    return SalesPerCategoryDTO.builder()
                            .categoryId(categoryId)
                            .categoryName(categoryName)
                            .totalRevenue(categoryRevenue)
                            .totalItemsSold(totalItemsSold)
                            .percentageOfTotalRevenue(percentageOfTotal)
                            .averageItemPrice(avgItemPrice)
                            .numberOfProducts((int) numberOfProducts)
                            .build();
                })
                .sorted(Comparator.comparing(SalesPerCategoryDTO::getTotalRevenue).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Calculate COGS and food cost percentage
     */
    public COGSAnalyticsDTO getCOGSAnalytics(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Order> orders = getCompletedOrders(startDateTime, endDateTime, restaurantId);

        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate COGS based on product cost prices
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

        BigDecimal grossProfit = totalRevenue.subtract(totalCOGS);

        BigDecimal foodCostPercentage = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? totalCOGS.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal grossProfitMargin = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return COGSAnalyticsDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalCOGS(totalCOGS)
                .totalRevenue(totalRevenue)
                .foodCostPercentage(foodCostPercentage)
                .grossProfitMargin(grossProfitMargin)
                .grossProfit(grossProfit)
                .build();
    }

    /**
     * Calculate comprehensive profitability metrics including labor costs
     * Note: Labor costs should be provided as input or calculated from employee/shift data
     */
    public ProfitabilityAnalyticsDTO getProfitabilityAnalytics(
            LocalDate startDate, LocalDate endDate, Long restaurantId,
            BigDecimal laborCosts, BigDecimal otherOperatingExpenses) {

        COGSAnalyticsDTO cogsAnalytics = getCOGSAnalytics(startDate, endDate, restaurantId);

        BigDecimal totalRevenue = cogsAnalytics.getTotalRevenue();
        BigDecimal totalCOGS = cogsAnalytics.getTotalCOGS();
        BigDecimal grossProfit = cogsAnalytics.getGrossProfit();
        BigDecimal grossProfitMargin = cogsAnalytics.getGrossProfitMargin();

        BigDecimal totalLaborCost = laborCosts != null ? laborCosts : BigDecimal.ZERO;
        BigDecimal totalOperatingExpenses = otherOperatingExpenses != null ? otherOperatingExpenses : BigDecimal.ZERO;

        BigDecimal cogsAndLaborCost = totalCOGS.add(totalLaborCost);
        BigDecimal netProfit = grossProfit.subtract(totalLaborCost).subtract(totalOperatingExpenses);

        BigDecimal laborCostPercentage = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? totalLaborCost.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal netProfitMargin = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? netProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal cogsAndLaborPercentage = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? cogsAndLaborCost.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return ProfitabilityAnalyticsDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalRevenue(totalRevenue)
                .totalCOGS(totalCOGS)
                .totalLaborCost(totalLaborCost)
                .totalOperatingExpenses(totalOperatingExpenses)
                .grossProfit(grossProfit)
                .netProfit(netProfit)
                .grossProfitMargin(grossProfitMargin)
                .netProfitMargin(netProfitMargin)
                .laborCostPercentage(laborCostPercentage)
                .cogsAndLaborCost(cogsAndLaborCost)
                .cogsAndLaborPercentage(cogsAndLaborPercentage)
                .build();
    }

    /**
     * Calculate contribution margin per menu item
     */
    public List<ContributionMarginDTO> getContributionMargins(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Order> orders = getCompletedOrders(startDateTime, endDateTime, restaurantId);

        // Group order items by product
        Map<Long, List<OrderItem>> itemsByProduct = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .collect(Collectors.groupingBy(OrderItem::getProductId));

        // Calculate total contribution across all products
        BigDecimal totalContribution = itemsByProduct.entrySet().stream()
                .map(entry -> {
                    Long productId = entry.getKey();
                    List<OrderItem> items = entry.getValue();
                    Product product = productRepository.findById(productId).orElse(null);
                    if (product != null && product.getCostPrice() != null) {
                        BigDecimal margin = product.getPrice().subtract(product.getCostPrice());
                        int totalSold = items.stream().mapToInt(OrderItem::getQuantity).sum();
                        return margin.multiply(BigDecimal.valueOf(totalSold));
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return itemsByProduct.entrySet().stream()
                .map(entry -> {
                    Long productId = entry.getKey();
                    List<OrderItem> items = entry.getValue();

                    Product product = productRepository.findById(productId).orElse(null);
                    if (product == null) {
                        return null;
                    }

                    BigDecimal sellingPrice = product.getPrice();
                    BigDecimal costPrice = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
                    BigDecimal contributionMargin = sellingPrice.subtract(costPrice);

                    BigDecimal contributionMarginRatio = sellingPrice.compareTo(BigDecimal.ZERO) > 0
                            ? contributionMargin.divide(sellingPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    int unitsSold = items.stream().mapToInt(OrderItem::getQuantity).sum();
                    BigDecimal totalContributionForProduct = contributionMargin.multiply(BigDecimal.valueOf(unitsSold));

                    BigDecimal percentageOfTotal = totalContribution.compareTo(BigDecimal.ZERO) > 0
                            ? totalContributionForProduct.divide(totalContribution, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    return ContributionMarginDTO.builder()
                            .productId(productId)
                            .productName(product.getName())
                            .categoryName(product.getCategory() != null ? product.getCategory().getName() : "Unknown")
                            .sellingPrice(sellingPrice)
                            .costPrice(costPrice)
                            .contributionMargin(contributionMargin)
                            .contributionMarginRatio(contributionMarginRatio)
                            .unitsSold(unitsSold)
                            .totalContribution(totalContributionForProduct)
                            .percentageOfTotalContribution(percentageOfTotal)
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ContributionMarginDTO::getTotalContribution).reversed())
                .collect(Collectors.toList());
    }

    // Helper methods

    private List<Order> getCompletedOrders(LocalDateTime startDateTime, LocalDateTime endDateTime, Long restaurantId) {
        if (restaurantId != null) {
            return orderRepository.findByRestaurantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    restaurantId, startDateTime, endDateTime
            ).stream()
                    .filter(order -> order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.DELIVERED)
                    .collect(Collectors.toList());
        } else {
            return orderRepository.findAll().stream()
                    .filter(order -> order.getCreatedAt().isAfter(startDateTime) && order.getCreatedAt().isBefore(endDateTime))
                    .filter(order -> order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.DELIVERED)
                    .collect(Collectors.toList());
        }
    }

    private BigDecimal calculateRevenueByPaymentMethod(List<Order> orders, PaymentMethod method) {
        return orders.stream()
                .filter(order -> order.getPayment() != null && order.getPayment().getMethod() == method)
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
