package com.elcafe.modules.pricing.service;

import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductIngredient;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.pricing.dto.PricingAnalyticsDTO;
import com.elcafe.modules.pricing.dto.PricingRecommendationDTO;
import com.elcafe.modules.pricing.dto.ProductProfitabilityDTO;
import com.elcafe.modules.pricing.enums.MenuEngineeringClass;
import com.elcafe.modules.pricing.enums.PricingStrategy;
import com.elcafe.modules.pricing.enums.ProfitabilityStatus;
import com.elcafe.modules.pricing.enums.RecommendationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for pricing strategy calculations and recommendations.
 * Uses shift-based time ranges for consistent reporting across midnight-crossing shifts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingStrategyService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ShiftTimeService shiftTimeService;

    // Default target margins
    private static final BigDecimal DEFAULT_TARGET_MARGIN = new BigDecimal("30.00");
    private static final BigDecimal MIN_ACCEPTABLE_MARGIN = new BigDecimal("15.00");
    private static final BigDecimal MAX_RECOMMENDED_INCREASE = new BigDecimal("15.00"); // Max 15% increase
    private static final BigDecimal POPULARITY_THRESHOLD = new BigDecimal("0.75"); // 75th percentile

    /**
     * Calculate cost-plus pricing for a product
     */
    public BigDecimal calculateCostPlusPrice(BigDecimal costPrice, BigDecimal targetMarginPercentage) {
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal marginMultiplier = BigDecimal.ONE.add(
                targetMarginPercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
        );

        return costPrice.multiply(marginMultiplier).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate ingredient-based cost for a product
     */
    public BigDecimal calculateIngredientCost(Product product) {
        if (product.getIngredients() == null || product.getIngredients().isEmpty()) {
            return product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
        }

        return product.getIngredients().stream()
                .map(ProductIngredient::calculateCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get comprehensive profitability analysis for a product
     */
    public ProductProfitabilityDTO getProductProfitability(Long productId, LocalDate startDate, LocalDate endDate) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        Long restaurantId = product.getCategory().getRestaurant().getId();

        // Get sales data
        List<Order> orders = getCompletedOrders(restaurantId, startDate, endDate);
        Map<Long, Long> salesByProduct = calculateSalesByProduct(orders);
        Map<Long, BigDecimal> revenueByProduct = calculateRevenueByProduct(orders);

        long unitsSold = salesByProduct.getOrDefault(productId, 0L);
        BigDecimal revenue = revenueByProduct.getOrDefault(productId, BigDecimal.ZERO);

        // Calculate costs
        BigDecimal sellingPrice = product.getPrice();
        BigDecimal ingredientCost = calculateIngredientCost(product);
        BigDecimal costPrice = product.getCostPrice() != null ? product.getCostPrice() : ingredientCost;

        // Calculate margins
        BigDecimal grossMargin = sellingPrice.subtract(costPrice);
        BigDecimal grossMarginPercentage = sellingPrice.compareTo(BigDecimal.ZERO) > 0
                ? grossMargin.divide(sellingPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal totalProfit = grossMargin.multiply(BigDecimal.valueOf(unitsSold));

        // Calculate sales velocity (units per day)
        long daysBetween = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate));
        BigDecimal salesVelocity = BigDecimal.valueOf(unitsSold).divide(BigDecimal.valueOf(daysBetween), 2, RoundingMode.HALF_UP);

        // Determine menu engineering class
        BigDecimal avgPopularity = calculateAveragePopularity(salesByProduct);
        BigDecimal avgMargin = calculateAverageMarginPercentage(restaurantId);

        MenuEngineeringClass menuClass = classifyMenuItem(grossMarginPercentage, avgMargin, unitsSold, avgPopularity.longValue());
        ProfitabilityStatus status = determineProfitabilityStatus(grossMarginPercentage);

        // Generate recommendation
        String recommendation = generateRecommendation(menuClass, grossMarginPercentage, status);
        BigDecimal suggestedPrice = calculateSuggestedPrice(product, menuClass, grossMarginPercentage);

        return ProductProfitabilityDTO.builder()
                .productId(productId)
                .productName(product.getName())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .sellingPrice(sellingPrice)
                .costPrice(costPrice)
                .ingredientCost(ingredientCost)
                .grossMargin(grossMargin)
                .grossMarginPercentage(grossMarginPercentage)
                .contributionMargin(grossMargin)
                .contributionMarginPercentage(grossMarginPercentage)
                .unitsSold(unitsSold)
                .totalRevenue(revenue)
                .totalProfit(totalProfit)
                .salesVelocity(salesVelocity)
                .menuClass(menuClass)
                .status(status)
                .recommendation(recommendation)
                .suggestedPrice(suggestedPrice)
                .targetMarginPercentage(DEFAULT_TARGET_MARGIN)
                .build();
    }

    /**
     * Get all products profitability for a restaurant
     */
    public List<ProductProfitabilityDTO> getAllProductsProfitability(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        List<Product> products = productRepository.findByRestaurant_Id(restaurantId);

        return products.stream()
                .map(product -> getProductProfitability(product.getId(), startDate, endDate))
                .sorted(Comparator.comparing(ProductProfitabilityDTO::getTotalProfit).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Generate pricing recommendations for all products
     */
    public List<PricingRecommendationDTO> generatePricingRecommendations(Long restaurantId, BigDecimal targetMargin) {
        if (targetMargin == null) {
            targetMargin = DEFAULT_TARGET_MARGIN;
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        List<Product> products = productRepository.findByRestaurant_Id(restaurantId);
        List<Order> orders = getCompletedOrders(restaurantId, startDate, endDate);

        Map<Long, Long> salesByProduct = calculateSalesByProduct(orders);
        Map<Long, BigDecimal> revenueByProduct = calculateRevenueByProduct(orders);

        BigDecimal avgMargin = calculateAverageMarginPercentage(restaurantId);
        BigDecimal avgPopularity = calculateAveragePopularity(salesByProduct);

        List<PricingRecommendationDTO> recommendations = new ArrayList<>();

        for (Product product : products) {
            BigDecimal costPrice = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;

            if (costPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Skip products without cost data
            }

            BigDecimal currentPrice = product.getPrice();
            BigDecimal currentMargin = currentPrice.subtract(costPrice);
            BigDecimal currentMarginPct = currentPrice.compareTo(BigDecimal.ZERO) > 0
                    ? currentMargin.divide(currentPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            // Calculate recommended price based on target margin
            BigDecimal recommendedPrice = calculateCostPlusPrice(costPrice, targetMargin);

            // Calculate price change
            BigDecimal priceChange = recommendedPrice.subtract(currentPrice);
            BigDecimal priceChangePct = currentPrice.compareTo(BigDecimal.ZERO) > 0
                    ? priceChange.divide(currentPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            // Limit maximum price increase
            if (priceChangePct.compareTo(MAX_RECOMMENDED_INCREASE) > 0) {
                recommendedPrice = currentPrice.multiply(
                        BigDecimal.ONE.add(MAX_RECOMMENDED_INCREASE.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
                ).setScale(2, RoundingMode.HALF_UP);
                priceChange = recommendedPrice.subtract(currentPrice);
                priceChangePct = MAX_RECOMMENDED_INCREASE;
            }

            BigDecimal projectedMargin = recommendedPrice.subtract(costPrice);
            BigDecimal projectedMarginPct = recommendedPrice.compareTo(BigDecimal.ZERO) > 0
                    ? projectedMargin.divide(recommendedPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            // Determine recommendation type
            RecommendationType recType = determineRecommendationType(currentMarginPct, targetMargin, priceChange);

            // Generate rationale
            String rationale = generateRationale(recType, currentMarginPct, targetMargin, priceChange);

            // Calculate confidence score
            int confidence = calculateConfidenceScore(product, salesByProduct.getOrDefault(product.getId(), 0L));

            long unitsSold = salesByProduct.getOrDefault(product.getId(), 0L);
            BigDecimal revenue = revenueByProduct.getOrDefault(product.getId(), BigDecimal.ZERO);

            recommendations.add(PricingRecommendationDTO.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .categoryName(product.getCategory().getName())
                    .currentPrice(currentPrice)
                    .currentCostPrice(costPrice)
                    .currentMargin(currentMargin)
                    .currentMarginPercentage(currentMarginPct)
                    .recommendedPrice(recommendedPrice)
                    .projectedMargin(projectedMargin)
                    .projectedMarginPercentage(projectedMarginPct)
                    .priceChange(priceChange)
                    .priceChangePercentage(priceChangePct)
                    .strategy(PricingStrategy.COST_PLUS)
                    .recommendationType(recType)
                    .rationale(rationale)
                    .confidenceScore(confidence)
                    .unitsSoldLast30Days(unitsSold)
                    .revenueLast30Days(revenue)
                    .generatedAt(LocalDateTime.now())
                    .build());
        }

        return recommendations.stream()
                .sorted(Comparator.comparing(r -> Math.abs(r.getPriceChange().doubleValue()), Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /**
     * Get pricing analytics summary for a restaurant
     */
    public PricingAnalyticsDTO getPricingAnalytics(Long restaurantId, LocalDate startDate, LocalDate endDate, BigDecimal targetMargin) {
        if (targetMargin == null) {
            targetMargin = DEFAULT_TARGET_MARGIN;
        }

        List<Product> products = productRepository.findByRestaurant_Id(restaurantId);
        List<Order> orders = getCompletedOrders(restaurantId, startDate, endDate);

        Map<Long, Long> salesByProduct = calculateSalesByProduct(orders);
        Map<Long, BigDecimal> revenueByProduct = calculateRevenueByProduct(orders);

        // Calculate overall metrics
        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCost = BigDecimal.ZERO;
        int productsWithCostData = 0;
        int productsNeedingReview = 0;
        int productsUnderperforming = 0;

        // Skip uncategorized products — groupingBy can't take a null key, and
        // we'd have no category bucket to put them under anyway.
        Map<Long, List<Product>> productsByCategory = products.stream()
                .filter(p -> p.getCategory() != null && p.getCategory().getId() != null)
                .collect(Collectors.groupingBy(p -> p.getCategory().getId()));

        // Menu engineering counts
        int stars = 0, plowHorses = 0, puzzles = 0, dogs = 0;

        BigDecimal avgPopularity = calculateAveragePopularity(salesByProduct);
        BigDecimal avgMargin = calculateAverageMarginPercentage(restaurantId);

        for (Product product : products) {
            if (product.getCostPrice() != null && product.getCostPrice().compareTo(BigDecimal.ZERO) > 0
                    && product.getPrice() != null) {
                productsWithCostData++;

                BigDecimal margin = product.getPrice().subtract(product.getCostPrice());
                BigDecimal marginPct = product.getPrice().compareTo(BigDecimal.ZERO) > 0
                        ? margin.divide(product.getPrice(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;

                long unitsSold = salesByProduct.getOrDefault(product.getId(), 0L);
                totalCost = totalCost.add(product.getCostPrice().multiply(BigDecimal.valueOf(unitsSold)));

                if (marginPct.compareTo(MIN_ACCEPTABLE_MARGIN) < 0) {
                    productsUnderperforming++;
                }

                if (Math.abs(marginPct.subtract(targetMargin).doubleValue()) > 10) {
                    productsNeedingReview++;
                }

                // Menu engineering classification
                MenuEngineeringClass menuClass = classifyMenuItem(marginPct, avgMargin, unitsSold, avgPopularity.longValue());
                switch (menuClass) {
                    case STAR -> stars++;
                    case PLOW_HORSE -> plowHorses++;
                    case PUZZLE -> puzzles++;
                    case DOG -> dogs++;
                }
            }
        }

        BigDecimal totalProfit = totalRevenue.subtract(totalCost);
        BigDecimal averageMargin = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? totalProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        BigDecimal marginGap = targetMargin.subtract(averageMargin);

        // Category breakdown
        List<PricingAnalyticsDTO.CategoryPricingDTO> categoryPricing = productsByCategory.entrySet().stream()
                .map(entry -> {
                    Long categoryId = entry.getKey();
                    List<Product> categoryProducts = entry.getValue();

                    String categoryName = categoryProducts.get(0).getCategory().getName();
                    int productCount = categoryProducts.size();

                    BigDecimal catRevenue = categoryProducts.stream()
                            .map(p -> revenueByProduct.getOrDefault(p.getId(), BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal catCost = categoryProducts.stream()
                            .map(p -> {
                                long units = salesByProduct.getOrDefault(p.getId(), 0L);
                                BigDecimal cost = p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO;
                                return cost.multiply(BigDecimal.valueOf(units));
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal catProfit = catRevenue.subtract(catCost);
                    BigDecimal catAvgPrice = categoryProducts.stream()
                            .map(Product::getPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(productCount), 2, RoundingMode.HALF_UP);

                    BigDecimal catAvgMargin = catRevenue.compareTo(BigDecimal.ZERO) > 0
                            ? catProfit.divide(catRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    BigDecimal revenueShare = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                            ? catRevenue.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    return PricingAnalyticsDTO.CategoryPricingDTO.builder()
                            .categoryId(categoryId)
                            .categoryName(categoryName)
                            .productCount(productCount)
                            .averagePrice(catAvgPrice)
                            .averageMargin(catAvgMargin)
                            .totalRevenue(catRevenue)
                            .totalProfit(catProfit)
                            .revenueShare(revenueShare)
                            .build();
                })
                .sorted(Comparator.comparing(PricingAnalyticsDTO.CategoryPricingDTO::getTotalRevenue).reversed())
                .collect(Collectors.toList());

        // Generate recommendations to count them
        List<PricingRecommendationDTO> recommendations = generatePricingRecommendations(restaurantId, targetMargin);
        int increaseRecs = (int) recommendations.stream().filter(r -> r.getRecommendationType() == RecommendationType.INCREASE).count();
        int decreaseRecs = (int) recommendations.stream().filter(r -> r.getRecommendationType() == RecommendationType.DECREASE).count();
        int maintainRecs = (int) recommendations.stream().filter(r -> r.getRecommendationType() == RecommendationType.MAINTAIN).count();

        return PricingAnalyticsDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .restaurantId(restaurantId)
                .averageMarginPercentage(averageMargin)
                .targetMarginPercentage(targetMargin)
                .marginGap(marginGap)
                .totalRevenue(totalRevenue)
                .totalCost(totalCost)
                .totalProfit(totalProfit)
                .totalProducts(products.size())
                .productsWithCostData(productsWithCostData)
                .productsNeedingReview(productsNeedingReview)
                .productsUnderperforming(productsUnderperforming)
                .categoryPricing(categoryPricing)
                .totalRecommendations(recommendations.size())
                .increaseRecommendations(increaseRecs)
                .decreaseRecommendations(decreaseRecs)
                .maintainRecommendations(maintainRecs)
                .menuEngineering(PricingAnalyticsDTO.MenuEngineeringSummary.builder()
                        .stars(stars)
                        .plowHorses(plowHorses)
                        .puzzles(puzzles)
                        .dogs(dogs)
                        .build())
                .build();
    }

    /**
     * Apply psychological pricing (e.g., $9.99 instead of $10.00)
     */
    public BigDecimal applyPsychologicalPricing(BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return price;
        }

        // Round to nearest .99
        BigDecimal rounded = price.setScale(0, RoundingMode.CEILING);
        return rounded.subtract(new BigDecimal("0.01"));
    }

    // Helper methods

    /**
     * Get orders with revenue-generating statuses within the shift time range.
     * Uses shared REVENUE_STATUSES for consistency across all reports.
     */
    private List<Order> getCompletedOrders(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        // Get shift-based time range
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, startDate, endDate);
        log.debug("Pricing analytics using shift range: {} to {}", shift.start(), shift.end());

        return orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                restaurantId, shift.start(), shift.end()
        ).stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .filter(order -> ShiftTimeService.REVENUE_STATUSES.contains(order.getStatus()))
                .collect(Collectors.toList());
    }

    private Map<Long, Long> calculateSalesByProduct(List<Order> orders) {
        // Skip items without a productId (synthetic packaging/addon lines);
        // groupingBy rejects null keys.
        return orders.stream()
                .flatMap(order -> order.getItems().stream())
                .filter(item -> item.getProductId() != null)
                .collect(Collectors.groupingBy(
                        OrderItem::getProductId,
                        Collectors.summingLong(OrderItem::getQuantity)
                ));
    }

    private Map<Long, BigDecimal> calculateRevenueByProduct(List<Order> orders) {
        return orders.stream()
                .flatMap(order -> order.getItems().stream())
                .filter(item -> item.getProductId() != null)
                .collect(Collectors.groupingBy(
                        OrderItem::getProductId,
                        Collectors.reducing(BigDecimal.ZERO,
                                item -> item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO,
                                BigDecimal::add)
                ));
    }

    private BigDecimal calculateAveragePopularity(Map<Long, Long> salesByProduct) {
        if (salesByProduct.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long totalSales = salesByProduct.values().stream().mapToLong(Long::longValue).sum();
        return BigDecimal.valueOf(totalSales).divide(BigDecimal.valueOf(salesByProduct.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverageMarginPercentage(Long restaurantId) {
        List<Product> products = productRepository.findByRestaurant_Id(restaurantId);

        List<BigDecimal> margins = products.stream()
                .filter(p -> p.getCostPrice() != null && p.getCostPrice().compareTo(BigDecimal.ZERO) > 0)
                .map(p -> {
                    BigDecimal margin = p.getPrice().subtract(p.getCostPrice());
                    return p.getPrice().compareTo(BigDecimal.ZERO) > 0
                            ? margin.divide(p.getPrice(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;
                })
                .collect(Collectors.toList());

        if (margins.isEmpty()) {
            return DEFAULT_TARGET_MARGIN;
        }

        return margins.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(margins.size()), 2, RoundingMode.HALF_UP);
    }

    private MenuEngineeringClass classifyMenuItem(BigDecimal marginPct, BigDecimal avgMargin, long unitsSold, long avgSales) {
        boolean highMargin = marginPct.compareTo(avgMargin) >= 0;
        boolean highPopularity = unitsSold >= avgSales;

        if (highMargin && highPopularity) {
            return MenuEngineeringClass.STAR;
        } else if (!highMargin && highPopularity) {
            return MenuEngineeringClass.PLOW_HORSE;
        } else if (highMargin && !highPopularity) {
            return MenuEngineeringClass.PUZZLE;
        } else {
            return MenuEngineeringClass.DOG;
        }
    }

    private ProfitabilityStatus determineProfitabilityStatus(BigDecimal marginPct) {
        if (marginPct.compareTo(new BigDecimal("40")) >= 0) {
            return ProfitabilityStatus.EXCELLENT;
        } else if (marginPct.compareTo(new BigDecimal("30")) >= 0) {
            return ProfitabilityStatus.HEALTHY;
        } else if (marginPct.compareTo(new BigDecimal("20")) >= 0) {
            return ProfitabilityStatus.ACCEPTABLE;
        } else if (marginPct.compareTo(new BigDecimal("10")) >= 0) {
            return ProfitabilityStatus.NEEDS_ATTENTION;
        } else {
            return ProfitabilityStatus.CRITICAL;
        }
    }

    private String generateRecommendation(MenuEngineeringClass menuClass, BigDecimal marginPct, ProfitabilityStatus status) {
        return switch (menuClass) {
            case STAR -> "This is a top performer. Maintain current pricing and promote prominently.";
            case PLOW_HORSE -> status == ProfitabilityStatus.CRITICAL
                    ? "High sales but very low margin. Consider gradual price increase or cost reduction."
                    : "Popular item with below-average margin. Consider slight price increase.";
            case PUZZLE -> "Good margins but low sales. Increase visibility through promotion or repositioning.";
            case DOG -> status == ProfitabilityStatus.CRITICAL
                    ? "Consider removing from menu or completely re-engineering."
                    : "Low performer. Review recipe costs and consider menu placement changes.";
        };
    }

    private BigDecimal calculateSuggestedPrice(Product product, MenuEngineeringClass menuClass, BigDecimal currentMarginPct) {
        BigDecimal costPrice = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
        if (costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return product.getPrice();
        }

        BigDecimal targetMargin = switch (menuClass) {
            case STAR -> currentMarginPct; // Keep current margin for stars
            case PLOW_HORSE -> DEFAULT_TARGET_MARGIN; // Increase to target
            case PUZZLE -> currentMarginPct.subtract(new BigDecimal("5")); // Slight decrease to boost sales
            case DOG -> DEFAULT_TARGET_MARGIN.add(new BigDecimal("5")); // Higher margin to compensate
        };

        return calculateCostPlusPrice(costPrice, targetMargin);
    }

    private RecommendationType determineRecommendationType(BigDecimal currentMarginPct, BigDecimal targetMargin, BigDecimal priceChange) {
        BigDecimal marginDiff = currentMarginPct.subtract(targetMargin).abs();

        if (marginDiff.compareTo(new BigDecimal("5")) <= 0) {
            return RecommendationType.MAINTAIN;
        }

        if (priceChange.compareTo(BigDecimal.ZERO) > 0) {
            return RecommendationType.INCREASE;
        } else if (priceChange.compareTo(BigDecimal.ZERO) < 0) {
            return RecommendationType.DECREASE;
        }

        return RecommendationType.REVIEW;
    }

    private String generateRationale(RecommendationType type, BigDecimal currentMarginPct, BigDecimal targetMargin, BigDecimal priceChange) {
        return switch (type) {
            case INCREASE -> String.format(
                    "Current margin (%.1f%%) is below target (%.1f%%). Recommend price increase of %.2f to improve profitability.",
                    currentMarginPct, targetMargin, priceChange.abs()
            );
            case DECREASE -> String.format(
                    "Current margin (%.1f%%) exceeds target (%.1f%%). Consider reducing price by %.2f to boost competitiveness.",
                    currentMarginPct, targetMargin, priceChange.abs()
            );
            case MAINTAIN -> String.format(
                    "Current margin (%.1f%%) is within acceptable range of target (%.1f%%). No change recommended.",
                    currentMarginPct, targetMargin
            );
            case REVIEW -> "Insufficient data for automated recommendation. Manual review suggested.";
            case DISCONTINUE -> "Item shows poor performance. Consider removal from menu.";
        };
    }

    private int calculateConfidenceScore(Product product, long unitsSold) {
        int score = 50; // Base score

        // Has cost data
        if (product.getCostPrice() != null && product.getCostPrice().compareTo(BigDecimal.ZERO) > 0) {
            score += 20;
        }

        // Has ingredient data
        if (product.getIngredients() != null && !product.getIngredients().isEmpty()) {
            score += 15;
        }

        // Has sales history
        if (unitsSold > 0) {
            score += 10;
        }
        if (unitsSold > 10) {
            score += 5;
        }

        return Math.min(100, score);
    }
}
