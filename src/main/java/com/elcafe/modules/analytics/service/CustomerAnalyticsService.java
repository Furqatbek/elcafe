package com.elcafe.modules.analytics.service;

import com.elcafe.config.CacheConfig;
import com.elcafe.modules.analytics.dto.CustomerLTVDTO;
import com.elcafe.modules.analytics.dto.CustomerRetentionDTO;
import com.elcafe.modules.analytics.dto.CustomerSatisfactionDTO;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.order.entity.Order;
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

        // Convert shift boundaries to LocalDateTime for comparison with Customer.createdAt
        LocalDateTime shiftStartLocal = shift.start().toLocalDateTime();
        LocalDateTime shiftEndLocal = shift.end().toLocalDateTime();

        // Get all customers that existed at the start of the period (strictly before start)
        List<Customer> customersAtStart = customerRepository.findByCreatedAtBefore(shiftStartLocal);

        // Get new customers during the period (inclusive boundaries for shift-aware consistency)
        List<Customer> newCustomers = customerRepository.findByCreatedAtBetween(shiftStartLocal, shiftEndLocal);

        // Get all customers at the end of the period (up to and including end)
        List<Customer> customersAtEnd = customerRepository.findByCreatedAtLessThanEqual(shiftEndLocal);

        // Get returning customers (customers who made orders during the period)
        Set<Long> returningCustomerIds = getCompletedOrders(shift.start(), shift.end(), restaurantId).stream()
                .filter(order -> order.getCustomer() != null)
                .map(order -> order.getCustomer().getId())
                .filter(customerId -> customersAtStart.stream().anyMatch(c -> c.getId().equals(customerId)))
                .collect(Collectors.toSet());

        int customersAtStartCount = customersAtStart.size();
        int newCustomersCount = newCustomers.size();
        int customersAtEndCount = customersAtEnd.size();
        int returningCustomersCount = returningCustomerIds.size();

        // Retention Rate = ((Customers at End - New Customers) / Customers at Start) * 100
        double retentionRate = customersAtStartCount > 0
                ? ((double) (customersAtEndCount - newCustomersCount) / customersAtStartCount) * 100
                : 0.0;

        // Churn Rate = 100 - Retention Rate
        double churnRate = 100.0 - retentionRate;

        // Count one-time vs repeat customers
        Map<Long, Long> orderCountByCustomer = getCompletedOrders(shift.start(), shift.end(), restaurantId).stream()
                .filter(order -> order.getCustomer() != null)
                .collect(Collectors.groupingBy(
                        order -> order.getCustomer().getId(),
                        Collectors.counting()
                ));

        long oneTimeCustomers = orderCountByCustomer.values().stream()
                .filter(count -> count == 1)
                .count();

        long repeatCustomers = orderCountByCustomer.values().stream()
                .filter(count -> count > 1)
                .count();

        double repeatCustomerRate = orderCountByCustomer.size() > 0
                ? ((double) repeatCustomers / orderCountByCustomer.size()) * 100
                : 0.0;

        return CustomerRetentionDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .customersAtStart(customersAtStartCount)
                .newCustomers(newCustomersCount)
                .customersAtEnd(customersAtEndCount)
                .returningCustomers(returningCustomersCount)
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
        // Use active customers only for LTV calculation
        List<Customer> allCustomers = customerRepository.findByActiveTrue();

        // Calculate metrics for each customer
        List<CustomerMetrics> customerMetrics = allCustomers.stream()
                .map(customer -> calculateCustomerMetrics(customer, restaurantId))
                .filter(Objects::nonNull)
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

        // Get all orders in the period
        List<Order> allOrders = getAllOrdersInPeriod(shift.start(), shift.end(), restaurantId);

        if (allOrders.isEmpty()) {
            return buildEmptySatisfactionDTO(startDate, endDate, restaurantId);
        }

        // Calculate operational satisfaction metrics
        int totalOrders = allOrders.size();

        // Completed orders (successfully delivered/served)
        long completedOrders = allOrders.stream()
                .filter(order -> ShiftTimeService.REVENUE_STATUSES.contains(order.getStatus()))
                .count();

        // Cancelled orders
        long cancelledOrders = allOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.CANCELLED)
                .count();

        // Calculate rates
        double completionRate = totalOrders > 0 ? (double) completedOrders / totalOrders * 100 : 0.0;
        double cancellationRate = totalOrders > 0 ? (double) cancelledOrders / totalOrders * 100 : 0.0;

        // Calculate repeat customer metrics
        Map<Long, Long> ordersByCustomer = allOrders.stream()
                .filter(order -> order.getCustomer() != null)
                .collect(Collectors.groupingBy(order -> order.getCustomer().getId(), Collectors.counting()));

        long repeatCustomerCount = ordersByCustomer.values().stream()
                .filter(count -> count > 1)
                .count();

        double repeatCustomerRate = ordersByCustomer.size() > 0
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

    /**
     * Get all orders in a time period (including cancelled) for satisfaction analysis.
     */
    private List<Order> getAllOrdersInPeriod(OffsetDateTime startDateTime, OffsetDateTime endDateTime, Long restaurantId) {
        if (restaurantId != null) {
            return orderRepository.findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    restaurantId, startDateTime, endDateTime);
        } else {
            return orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDateTime, endDateTime);
        }
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

    /**
     * Get orders with revenue-generating statuses within the given time range.
     * Uses shared REVENUE_STATUSES for consistency across all reports.
     */
    private List<Order> getCompletedOrders(OffsetDateTime startDateTime, OffsetDateTime endDateTime, Long restaurantId) {
        List<Order> orders;
        if (restaurantId != null) {
            orders = orderRepository.findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    restaurantId,
                    startDateTime,
                    endDateTime
            );
        } else {
            // Use proper repository query instead of findAll()
            orders = orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDateTime, endDateTime);
        }

        return orders.stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .filter(order -> ShiftTimeService.REVENUE_STATUSES.contains(order.getStatus()))
                .collect(Collectors.toList());
    }

    private CustomerMetrics calculateCustomerMetrics(Customer customer, Long restaurantId) {
        List<Order> customerOrders = orderRepository.findByCustomer_IdOrderByCreatedAtDesc(customer.getId()).stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .filter(order -> ShiftTimeService.REVENUE_STATUSES.contains(order.getStatus()))
                .filter(order -> restaurantId == null || order.getRestaurant().getId().equals(restaurantId))
                .collect(Collectors.toList());

        if (customerOrders.isEmpty()) {
            return null;
        }

        // Total spent
        BigDecimal totalSpent = customerOrders.stream()
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Order count
        int orderCount = customerOrders.size();

        // Customer lifespan (from first to last order)
        LocalDateTime firstOrderDate = customerOrders.stream()
                .map(order -> order.getCreatedAt().toLocalDateTime())
                .min(LocalDateTime::compareTo)
                .orElse(customer.getCreatedAt());

        LocalDateTime lastOrderDate = customerOrders.stream()
                .map(order -> order.getCreatedAt().toLocalDateTime())
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

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
