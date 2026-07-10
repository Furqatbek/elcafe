package com.elcafe.modules.analytics.service;

import com.elcafe.config.CacheConfig;
import com.elcafe.modules.analytics.dto.CustomerLTVDTO;
import com.elcafe.modules.analytics.dto.CustomerRetentionDTO;
import com.elcafe.modules.analytics.dto.CustomerSatisfactionDTO;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.order.dto.CustomerLifetimeRow;
import com.elcafe.modules.order.dto.CustomerOrderCountRow;
import com.elcafe.modules.order.dto.CustomerOrderStatsRow;
import com.elcafe.modules.order.dto.OrderVolumeCountsRow;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for customer analytics calculations.
 * Uses shift-based time ranges for consistent reporting across midnight-crossing shifts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAnalyticsService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final ShiftTimeService shiftTimeService;

    /**
     * Calculate customer retention rate.
     * Uses shift-based time ranges for restaurants with midnight-crossing shifts.
     */
    @Cacheable(value = CacheConfig.CUSTOMER_RETENTION,
               key = "'retention:' + #restaurantId + ':' + #startDate + ':' + #endDate",
               unless = "#result == null")
    public CustomerRetentionDTO getCustomerRetention(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        // Get shift-based time range
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, startDate, endDate);
        log.debug("Customer retention using shift range: {} to {}", shift.start(), shift.end());

        // Customer.createdAt is OffsetDateTime; pass the shift boundaries
        // through unchanged. The repo previously declared LocalDateTime
        // params which Hibernate's strict bind-type check rejected.
        OffsetDateTime shiftStartOffset = shift.start();
        OffsetDateTime shiftEndOffset = shift.end();

        // Population counts, computed in the DB (the old code loaded three full customer lists to size())
        long customersAtStartCount = customerRepository.countByCreatedAtBefore(shiftStartOffset);
        long newCustomersCount = customerRepository.countByCreatedAtBetween(shiftStartOffset, shiftEndOffset);
        long customersAtEndCount = customerRepository.countByCreatedAtLessThanEqual(shiftEndOffset);

        // Per-customer order counts over revenue-status orders in the period, one grouped query —
        // serves both the returning-customer count and the one-time/repeat split
        List<CustomerOrderStatsRow> customerStats = orderRepository.findCustomerOrderStats(
                restaurantId, shift.start(), shift.end(), ShiftTimeService.REVENUE_STATUSES);

        // Returning customers = ordered in the period AND existed strictly before it started
        long returningCustomersCount = customerStats.stream()
                .filter(row -> row.customerCreatedAt() != null && row.customerCreatedAt().isBefore(shiftStartOffset))
                .count();

        // Retention Rate = ((Customers at End - New Customers) / Customers at Start) * 100
        double retentionRate = customersAtStartCount > 0
                ? ((double) (customersAtEndCount - newCustomersCount) / customersAtStartCount) * 100
                : 0.0;

        // Churn Rate = 100 - Retention Rate
        double churnRate = 100.0 - retentionRate;

        // Count one-time vs repeat customers
        long oneTimeCustomers = customerStats.stream()
                .filter(row -> row.orderCount() == 1)
                .count();

        long repeatCustomers = customerStats.stream()
                .filter(row -> row.orderCount() > 1)
                .count();

        double repeatCustomerRate = !customerStats.isEmpty()
                ? ((double) repeatCustomers / customerStats.size()) * 100
                : 0.0;

        return CustomerRetentionDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .customersAtStart((int) customersAtStartCount)
                .newCustomers((int) newCustomersCount)
                .customersAtEnd((int) customersAtEndCount)
                .returningCustomers((int) returningCustomersCount)
                .retentionRate(retentionRate)
                .churnRate(churnRate)
                .oneTimeCustomers((int) oneTimeCustomers)
                .repeatCustomers((int) repeatCustomers)
                .repeatCustomerRate(repeatCustomerRate)
                .build();
    }

    /**
     * Calculate Customer Lifetime Value (CLV)
     */
    @Cacheable(value = CacheConfig.CUSTOMER_LTV,
               key = "'ltv:' + #restaurantId",
               unless = "#result == null")
    public CustomerLTVDTO getCustomerLTV(Long restaurantId) {
        // One grouped query: per-active-customer lifetime sums over revenue-status orders. The old code
        // loaded EVERY active customer's ENTIRE order history one customer at a time — the single worst
        // loader in the codebase (audit E3). Customers without qualifying orders drop out of the GROUP BY
        // exactly as calculateCustomerMetrics used to return null for them.
        List<CustomerMetrics> customerMetrics = orderRepository
                .findCustomerLifetimeStats(restaurantId, ShiftTimeService.REVENUE_STATUSES).stream()
                .map(CustomerAnalyticsService::toCustomerMetrics)
                .collect(Collectors.toList());

        if (customerMetrics.isEmpty()) {
            return CustomerLTVDTO.builder()
                    .averageCustomerLTV(BigDecimal.ZERO)
                    .medianCustomerLTV(BigDecimal.ZERO)
                    .averageOrderValue(BigDecimal.ZERO)
                    .averagePurchaseFrequency(0.0)
                    .averageCustomerLifespanDays(0.0)
                    .totalCustomerValue(BigDecimal.ZERO)
                    .totalCustomersAnalyzed(0)
                    .topTierCustomerLTV(BigDecimal.ZERO)
                    .lowTierCustomerLTV(BigDecimal.ZERO)
                    .build();
        }

        // Average CLV
        BigDecimal totalLTV = customerMetrics.stream()
                .map(CustomerMetrics::getLtv)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageLTV = totalLTV.divide(
                BigDecimal.valueOf(customerMetrics.size()),
                2,
                RoundingMode.HALF_UP
        );

        // Median CLV
        List<BigDecimal> sortedLTVs = customerMetrics.stream()
                .map(CustomerMetrics::getLtv)
                .sorted()
                .collect(Collectors.toList());

        BigDecimal medianLTV = calculateMedian(sortedLTVs);

        // Average order value
        BigDecimal totalOrderValue = customerMetrics.stream()
                .map(CustomerMetrics::getTotalSpent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalOrders = customerMetrics.stream()
                .mapToInt(CustomerMetrics::getOrderCount)
                .sum();

        BigDecimal avgOrderValue = totalOrders > 0
                ? totalOrderValue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Average purchase frequency
        double avgPurchaseFrequency = customerMetrics.stream()
                .mapToInt(CustomerMetrics::getOrderCount)
                .average()
                .orElse(0.0);

        // Average customer lifespan
        double avgLifespanDays = customerMetrics.stream()
                .mapToLong(CustomerMetrics::getLifespanDays)
                .average()
                .orElse(0.0);

        // Top tier (top 10%) and low tier (bottom 50%)
        int topTierIndex = Math.max(0, (int) (sortedLTVs.size() * 0.9) - 1);
        int lowTierIndex = Math.min(sortedLTVs.size() - 1, (int) (sortedLTVs.size() * 0.5));

        BigDecimal topTierLTV = sortedLTVs.size() > 0 ? sortedLTVs.get(topTierIndex) : BigDecimal.ZERO;
        BigDecimal lowTierLTV = sortedLTVs.size() > 0 ? sortedLTVs.get(lowTierIndex) : BigDecimal.ZERO;

        return CustomerLTVDTO.builder()
                .averageCustomerLTV(averageLTV)
                .medianCustomerLTV(medianLTV)
                .averageOrderValue(avgOrderValue)
                .averagePurchaseFrequency(avgPurchaseFrequency)
                .averageCustomerLifespanDays(avgLifespanDays)
                .totalCustomerValue(totalLTV)
                .totalCustomersAnalyzed(customerMetrics.size())
                .topTierCustomerLTV(topTierLTV)
                .lowTierCustomerLTV(lowTierLTV)
                .build();
    }

    /**
     * Calculate customer satisfaction score using order-based proxy metrics.
     * <p>
     * This implementation calculates satisfaction based on operational metrics:
     * - Order completion rate (successfully delivered/served orders)
     * - Order cancellation rate
     * - Repeat customer rate (indicator of satisfaction)
     * <p>
     * For full external review integration (Google, Yandex, Telegram),
     * implement dedicated ReviewIntegrationService with API clients.
     */
    public CustomerSatisfactionDTO getCustomerSatisfaction(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        // Get shift-based time range for consistent reporting
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, startDate, endDate);
        log.debug("Customer satisfaction using shift range: {} to {}", shift.start(), shift.end());

        // Order volumes over ALL statuses in the period, counted in the DB
        OrderVolumeCountsRow volumes = orderRepository.countOrderVolumes(
                restaurantId, shift.start(), shift.end(), ShiftTimeService.REVENUE_STATUSES, OrderStatus.CANCELLED);

        int totalOrders = volumes.totalOrders().intValue();
        if (totalOrders == 0) {
            return buildEmptySatisfactionDTO(startDate, endDate, restaurantId);
        }

        long completedOrders = volumes.completedOrders();
        long cancelledOrders = volumes.cancelledOrders();

        // Calculate rates
        double completionRate = totalOrders > 0 ? (double) completedOrders / totalOrders * 100 : 0.0;
        double cancellationRate = totalOrders > 0 ? (double) cancelledOrders / totalOrders * 100 : 0.0;

        // Calculate repeat customer metrics from grouped per-customer counts
        List<CustomerOrderCountRow> ordersByCustomer = orderRepository.findCustomerOrderCounts(
                restaurantId, shift.start(), shift.end());

        long repeatCustomerCount = ordersByCustomer.stream()
                .filter(row -> row.orderCount() > 1)
                .count();

        double repeatCustomerRate = !ordersByCustomer.isEmpty()
                ? (double) repeatCustomerCount / ordersByCustomer.size() * 100 : 0.0;

        // Calculate internal satisfaction score (weighted average of operational metrics)
        // Completion rate (40%), inverse cancellation rate (30%), repeat customer rate (30%)
        double internalScore = (completionRate * 0.4) +
                              ((100 - cancellationRate) * 0.3) +
                              (repeatCustomerRate * 0.3);

        // Convert to 5-star scale (0-100% -> 0-5 stars)
        double internalRating = internalScore / 20.0;

        // Categorize based on internal score
        int positiveCount = (int) (internalScore >= 80 ? completedOrders : completedOrders * (internalScore / 100));
        int negativeCount = (int) cancelledOrders;
        int neutralCount = Math.max(0, totalOrders - positiveCount - negativeCount);

        double positivePercentage = totalOrders > 0 ? (double) positiveCount / totalOrders * 100 : 0.0;
        double negativePercentage = totalOrders > 0 ? (double) negativeCount / totalOrders * 100 : 0.0;

        return CustomerSatisfactionDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .restaurantId(restaurantId)
                .overallSatisfactionScore(internalScore)
                .googleRating(0.0)  // External integration pending
                .googleReviewCount(0)
                .yandexRating(0.0)  // External integration pending
                .yandexReviewCount(0)
                .telegramRating(0.0)  // External integration pending
                .telegramReviewCount(0)
                .internalRating(internalRating)
                .internalReviewCount(totalOrders)
                .totalReviews(totalOrders)
                .averageRating(internalRating)
                .positiveReviews(positiveCount)
                .neutralReviews(neutralCount)
                .negativeReviews(negativeCount)
                .positivePercentage(positivePercentage)
                .negativePercentage(negativePercentage)
                .build();
    }

    private CustomerSatisfactionDTO buildEmptySatisfactionDTO(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        return CustomerSatisfactionDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .restaurantId(restaurantId)
                .overallSatisfactionScore(0.0)
                .googleRating(0.0)
                .googleReviewCount(0)
                .yandexRating(0.0)
                .yandexReviewCount(0)
                .telegramRating(0.0)
                .telegramReviewCount(0)
                .internalRating(0.0)
                .internalReviewCount(0)
                .totalReviews(0)
                .averageRating(0.0)
                .positiveReviews(0)
                .neutralReviews(0)
                .negativeReviews(0)
                .positivePercentage(0.0)
                .negativePercentage(0.0)
                .build();
    }

    // Helper methods

    /** Same math as the old per-customer entity walk, applied to one aggregate row. */
    private static CustomerMetrics toCustomerMetrics(CustomerLifetimeRow row) {
        BigDecimal totalSpent = row.totalSpent();
        int orderCount = row.orderCount().intValue();

        // Customer lifespan (from first to last order); rows always carry both (COUNT >= 1)
        LocalDateTime firstOrderDate = row.firstOrderAt().toLocalDateTime();
        LocalDateTime lastOrderDate = row.lastOrderAt().toLocalDateTime();

        long lifespanDays = ChronoUnit.DAYS.between(firstOrderDate, lastOrderDate);
        if (lifespanDays == 0) {
            lifespanDays = 1; // Minimum 1 day
        }

        // Calculate LTV: Total Spent * (Purchase Frequency / Lifespan) * Average Customer Lifespan
        // Simplified: We'll use Total Spent as a proxy for LTV
        // In a more sophisticated model, you'd project future value
        BigDecimal ltv = totalSpent;

        return new CustomerMetrics(totalSpent, orderCount, lifespanDays, ltv);
    }

    private BigDecimal calculateMedian(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        int size = values.size();
        if (size % 2 == 0) {
            BigDecimal mid1 = values.get(size / 2 - 1);
            BigDecimal mid2 = values.get(size / 2);
            return mid1.add(mid2).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        } else {
            return values.get(size / 2);
        }
    }

    // Internal class for customer metrics
    private static class CustomerMetrics {
        private final BigDecimal totalSpent;
        private final int orderCount;
        private final long lifespanDays;
        private final BigDecimal ltv;

        public CustomerMetrics(BigDecimal totalSpent, int orderCount, long lifespanDays, BigDecimal ltv) {
            this.totalSpent = totalSpent;
            this.orderCount = orderCount;
            this.lifespanDays = lifespanDays;
            this.ltv = ltv;
        }

        public BigDecimal getTotalSpent() { return totalSpent; }
        public int getOrderCount() { return orderCount; }
        public long getLifespanDays() { return lifespanDays; }
        public BigDecimal getLtv() { return ltv; }
    }
}
