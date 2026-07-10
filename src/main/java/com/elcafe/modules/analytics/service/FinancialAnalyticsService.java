package com.elcafe.modules.analytics.service;

import com.elcafe.config.CacheConfig;
import com.elcafe.modules.analytics.dto.*;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.inventory.repository.ProductionBatchConsumptionRepository;
import com.elcafe.modules.inventory.service.BatchConsumptionService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.dto.ProductSalesRow;
import com.elcafe.modules.order.dto.RevenueOrderRow;
import com.elcafe.modules.order.dto.RevenueTotalsRow;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for financial analytics calculations.
 * Uses shift-based time ranges for consistent reporting across midnight-crossing shifts.
 *
 * <p><b>Aggregation strategy (audit PERF-2):</b> all order scans run as database aggregates / scalar
 * projections ({@code OrderRepository.REVENUE_QUALIFYING_WHERE}) instead of materializing the range's
 * {@code Order} entity graphs and reducing in Java — a year of a busy tenant used to mean tens of
 * thousands of entities plus an items/payments N+1 and could OOM the heap. The revenue-qualifying
 * filter is the exact SQL translation of the old in-Java filter; business-day attribution stays in
 * Java (it is weekday- and shift-dependent) but now runs on scalar rows with the restaurant's business
 * hours preloaded once ({@link ShiftTimeService#businessDayResolver}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialAnalyticsService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final BatchConsumptionService batchConsumptionService;
    private final ProductionBatchConsumptionRepository productionBatchConsumptionRepository;
    private final PayrollEntryRepository payrollEntryRepository;
    private final ShiftTimeService shiftTimeService;

    /**
     * Calculate daily revenue for a date range.
     * Uses shift-based time ranges for restaurants with midnight-crossing shifts.
     */
    @Cacheable(value = CacheConfig.DAILY_REVENUE,
               key = "'revenue:' + #restaurantId + ':' + #startDate + ':' + #endDate",
               unless = "#result == null || #result.isEmpty()")
    public List<DailyRevenueDTO> getDailyRevenue(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        // Get shift-based time range
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, startDate, endDate);
        log.debug("Daily revenue using shift range: {} to {}", shift.start(), shift.end());

        // One scalar row per qualifying order (created-at, total, first payment method) — no entity graphs
        List<RevenueOrderRow> rows = findRevenueOrderRows(shift.start(), shift.end(), restaurantId);

        // Group by business day (not calendar date) for shift-aware reporting; hours preloaded once
        // This ensures orders after midnight but before the next shift are attributed to the previous business day
        ShiftTimeService.BusinessDayResolver businessDays = shiftTimeService.businessDayResolver(restaurantId);
        Map<LocalDate, List<RevenueOrderRow>> ordersByDate = rows.stream()
                .collect(Collectors.groupingBy(row ->
                        businessDays.businessDayFor(row.createdAt().toLocalDateTime())));

        return ordersByDate.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<RevenueOrderRow> dailyOrders = entry.getValue();

                    BigDecimal totalRevenue = dailyOrders.stream()
                            .map(RevenueOrderRow::total)
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
     * Calculate sales per category.
     * Uses shift-based time ranges for restaurants with midnight-crossing shifts.
     */
    @Cacheable(value = CacheConfig.SALES_BY_CATEGORY,
               key = "'category:' + #restaurantId + ':' + #startDate + ':' + #endDate",
               unless = "#result == null || #result.isEmpty()")
    public List<SalesPerCategoryDTO> getSalesPerCategory(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        // Get shift-based time range
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, startDate, endDate);
        log.debug("Sales per category using shift range: {} to {}", shift.start(), shift.end());

        // Total revenue base is the SUM of qualifying orders' totals (not the item sums)
        BigDecimal totalRevenue = sumRevenueTotals(shift.start(), shift.end(), restaurantId).totalRevenue();

        // Per-product revenue/quantity, aggregated in the DB (bundle/packaging rows with null productId
        // are excluded by the query, matching the old OrderItem.groupByProductId contract)
        List<ProductSalesRow> productSales = sumProductSales(shift.start(), shift.end(), restaurantId);

        // Get all products
        Set<Long> productIds = productSales.stream().map(ProductSalesRow::productId).collect(Collectors.toSet());
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Group per-product sums by category
        Map<Long, List<ProductSalesRow>> salesByCategory = new HashMap<>();
        for (ProductSalesRow row : productSales) {
            Product product = products.get(row.productId());
            if (product != null && product.getCategory() != null) {
                salesByCategory.computeIfAbsent(product.getCategory().getId(), k -> new ArrayList<>()).add(row);
            }
        }

        return salesByCategory.entrySet().stream()
                .map(entry -> {
                    Long categoryId = entry.getKey();
                    List<ProductSalesRow> categorySales = entry.getValue();

                    // Get category name from first product
                    String categoryName = categorySales.stream()
                            .map(row -> products.get(row.productId()))
                            .filter(Objects::nonNull)
                            .findFirst()
                            .map(p -> p.getCategory().getName())
                            .orElse("Unknown");

                    BigDecimal categoryRevenue = categorySales.stream()
                            .map(ProductSalesRow::revenue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    int totalItemsSold = categorySales.stream()
                            .mapToInt(row -> row.quantitySold().intValue())
                            .sum();

                    BigDecimal percentageOfTotal = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                            ? categoryRevenue.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    BigDecimal avgItemPrice = totalItemsSold > 0
                            ? categoryRevenue.divide(BigDecimal.valueOf(totalItemsSold), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    // One ProductSalesRow per distinct product (the query groups by productId)
                    int numberOfProducts = categorySales.size();

                    return SalesPerCategoryDTO.builder()
                            .categoryId(categoryId)
                            .categoryName(categoryName)
                            .totalRevenue(categoryRevenue)
                            .totalItemsSold(totalItemsSold)
                            .percentageOfTotalRevenue(percentageOfTotal)
                            .averageItemPrice(avgItemPrice)
                            .numberOfProducts(numberOfProducts)
                            .build();
                })
                .sorted(Comparator.comparing(SalesPerCategoryDTO::getTotalRevenue).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Calculate COGS and food cost percentage.
     * Uses actual batch consumption data when available, falls back to product cost prices.
     * Uses shift-based time ranges for restaurants with midnight-crossing shifts.
     */
    @Cacheable(value = CacheConfig.COGS_ANALYTICS,
               key = "'cogs:' + #restaurantId + ':' + #startDate + ':' + #endDate",
               unless = "#result == null")
    public COGSAnalyticsDTO getCOGSAnalytics(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        // Get shift-based time range
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, startDate, endDate);
        log.debug("COGS analytics using shift range: {} to {}", shift.start(), shift.end());

        // Total revenue over qualifying orders, aggregated in the DB
        BigDecimal totalRevenue = sumRevenueTotals(shift.start(), shift.end(), restaurantId).totalRevenue();

        // Try to get COGS from actual batch consumption records first
        BigDecimal batchBasedCOGS = BigDecimal.ZERO;
        if (restaurantId != null) {
            try {
                batchBasedCOGS = batchConsumptionService.calculateTotalCOGS(restaurantId, shift.start().toLocalDateTime(), shift.end().toLocalDateTime());
            } catch (Exception e) {
                log.warn("Could not calculate batch-based COGS for restaurant {}, falling back to product costs: {}",
                         restaurantId, e.getMessage());
            }
        }

        // Per-product quantities from the DB aggregate; cost prices applied from a single batch product load
        List<ProductSalesRow> productSales = sumProductSales(shift.start(), shift.end(), restaurantId);
        Set<Long> productIds = productSales.stream().map(ProductSalesRow::productId).collect(Collectors.toSet());
        Map<Long, Product> productsMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Calculate COGS based on product cost prices as fallback/comparison
        BigDecimal productBasedCOGS = productSales.stream()
                .map(row -> {
                    Product product = productsMap.get(row.productId());
                    if (product != null && product.getCostPrice() != null) {
                        return product.getCostPrice().multiply(BigDecimal.valueOf(row.quantitySold()));
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Include production batch COGS (prepared items like soups, stews)
        BigDecimal productionBatchCOGS = BigDecimal.ZERO;
        if (restaurantId != null) {
            try {
                productionBatchCOGS = productionBatchConsumptionRepository.getTotalConsumptionCost(
                        restaurantId, shift.start().toLocalDateTime(), shift.end().toLocalDateTime());
            } catch (Exception e) {
                log.warn("Could not calculate production batch COGS for restaurant {}: {}",
                         restaurantId, e.getMessage());
            }
        }

        // Use batch-based COGS if available and meaningful, otherwise use product-based
        // Always add production batch COGS on top
        BigDecimal rawIngredientCOGS = batchBasedCOGS.compareTo(BigDecimal.ZERO) > 0
                ? batchBasedCOGS
                : productBasedCOGS;
        BigDecimal totalCOGS = rawIngredientCOGS.add(productionBatchCOGS);

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
    @Cacheable(value = CacheConfig.PROFITABILITY,
               key = "'profit:' + #restaurantId + ':' + #startDate + ':' + #endDate + ':' + #laborCosts + ':' + #operatingExpenses",
               unless = "#result == null")
    public ProfitabilityAnalyticsDTO getProfitabilityAnalytics(
            LocalDate startDate, LocalDate endDate, Long restaurantId,
            BigDecimal laborCosts, BigDecimal otherOperatingExpenses) {

        COGSAnalyticsDTO cogsAnalytics = getCOGSAnalytics(startDate, endDate, restaurantId);

        BigDecimal totalRevenue = cogsAnalytics.getTotalRevenue();
        BigDecimal totalCOGS = cogsAnalytics.getTotalCOGS();
        BigDecimal grossProfit = cogsAnalytics.getGrossProfit();
        BigDecimal grossProfitMargin = cogsAnalytics.getGrossProfitMargin();

        BigDecimal totalLaborCost = laborCosts != null && laborCosts.compareTo(BigDecimal.ZERO) > 0
                ? laborCosts
                : fetchPayrollCosts(restaurantId, startDate, endDate);
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
     * Calculate contribution margin per menu item.
     * Uses shift-based time ranges for restaurants with midnight-crossing shifts.
     */
    @Cacheable(value = CacheConfig.CONTRIBUTION_MARGINS,
               key = "'margins:' + #restaurantId + ':' + #startDate + ':' + #endDate",
               unless = "#result == null || #result.isEmpty()")
    public List<ContributionMarginDTO> getContributionMargins(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        // Get shift-based time range
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, startDate, endDate);
        log.debug("Contribution margins using shift range: {} to {}", shift.start(), shift.end());

        // Per-product quantities aggregated in the DB (null productIds excluded by the query)
        List<ProductSalesRow> productSales = sumProductSales(shift.start(), shift.end(), restaurantId);

        // Batch load all products to avoid N+1 queries
        Set<Long> productIds = productSales.stream().map(ProductSalesRow::productId).collect(Collectors.toSet());
        Map<Long, Product> productsMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Calculate total contribution across all products
        BigDecimal totalContribution = productSales.stream()
                .map(row -> {
                    Product product = productsMap.get(row.productId());
                    if (product != null && product.getCostPrice() != null && product.getPrice() != null) {
                        BigDecimal margin = product.getPrice().subtract(product.getCostPrice());
                        return margin.multiply(BigDecimal.valueOf(row.quantitySold()));
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return productSales.stream()
                .map(row -> {
                    Product product = productsMap.get(row.productId());
                    if (product == null) {
                        return null;
                    }

                    BigDecimal sellingPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                    BigDecimal costPrice = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
                    BigDecimal contributionMargin = sellingPrice.subtract(costPrice);

                    BigDecimal contributionMarginRatio = sellingPrice.compareTo(BigDecimal.ZERO) > 0
                            ? contributionMargin.divide(sellingPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    int unitsSold = row.quantitySold().intValue();
                    BigDecimal totalContributionForProduct = contributionMargin.multiply(BigDecimal.valueOf(unitsSold));

                    BigDecimal percentageOfTotal = totalContribution.compareTo(BigDecimal.ZERO) > 0
                            ? totalContributionForProduct.divide(totalContribution, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    return ContributionMarginDTO.builder()
                            .productId(row.productId())
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

    /**
     * The revenue-qualifying filter parameters shared by every aggregate call. The WHERE itself lives in
     * {@code OrderRepository.REVENUE_QUALIFYING_WHERE}; these wrappers keep the enum plumbing in one place.
     */
    private List<RevenueOrderRow> findRevenueOrderRows(OffsetDateTime start, OffsetDateTime end, Long restaurantId) {
        return orderRepository.findRevenueOrderRows(restaurantId, start, end,
                OrderStatus.CANCELLED, ShiftTimeService.REVENUE_STATUSES, PaymentStatus.COMPLETED);
    }

    private List<ProductSalesRow> sumProductSales(OffsetDateTime start, OffsetDateTime end, Long restaurantId) {
        return orderRepository.sumProductSales(restaurantId, start, end,
                OrderStatus.CANCELLED, ShiftTimeService.REVENUE_STATUSES, PaymentStatus.COMPLETED);
    }

    private RevenueTotalsRow sumRevenueTotals(OffsetDateTime start, OffsetDateTime end, Long restaurantId) {
        return orderRepository.sumRevenueTotals(restaurantId, start, end,
                OrderStatus.CANCELLED, ShiftTimeService.REVENUE_STATUSES, PaymentStatus.COMPLETED);
    }

    private BigDecimal calculateRevenueByPaymentMethod(List<RevenueOrderRow> orders, PaymentMethod method) {
        return orders.stream()
                .filter(row -> row.firstPaymentMethod() == method)
                .map(RevenueOrderRow::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal fetchPayrollCosts(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        BigDecimal payroll = payrollEntryRepository.getTotalPaidPayrollByPaymentDate(restaurantId, startDate, endDate);
        return payroll != null ? payroll : BigDecimal.ZERO;
    }
}
