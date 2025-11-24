package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.CustomerLTVDTO;
import com.elcafe.modules.analytics.dto.CustomerRetentionDTO;
import com.elcafe.modules.analytics.dto.CustomerSatisfactionDTO;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.order.entity.Order;
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
 * Service for customer analytics calculations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAnalyticsService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    /**
     * Calculate customer retention rate
     */
    public CustomerRetentionDTO getCustomerRetention(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // Get all customers that existed at the start of the period
        List<Customer> customersAtStart = customerRepository.findAll().stream()
                .filter(c -> c.getCreatedAt().isBefore(startDateTime))
                .collect(Collectors.toList());

        // Get new customers during the period
        List<Customer> newCustomers = customerRepository.findAll().stream()
                .filter(c -> c.getCreatedAt().isAfter(startDateTime) && c.getCreatedAt().isBefore(endDateTime))
                .collect(Collectors.toList());

        // Get all customers at the end of the period
        List<Customer> customersAtEnd = customerRepository.findAll().stream()
                .filter(c -> c.getCreatedAt().isBefore(endDateTime))
                .collect(Collectors.toList());

        // Get returning customers (customers who made orders during the period)
        Set<Long> returningCustomerIds = getCompletedOrders(startDateTime, endDateTime, restaurantId).stream()
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
        Map<Long, Long> orderCountByCustomer = getCompletedOrders(startDateTime, endDateTime, restaurantId).stream()
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
    public CustomerLTVDTO getCustomerLTV(Long restaurantId) {
        List<Customer> allCustomers = customerRepository.findAll();

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
     * Calculate customer satisfaction score
     * Note: This is a placeholder implementation. In a real system, you would integrate with
     * review/rating APIs (Google, Yandex, Telegram) and internal feedback systems.
     */
    public CustomerSatisfactionDTO getCustomerSatisfaction(LocalDate startDate, LocalDate endDate) {
        // TODO: Integrate with actual review/rating systems
        // For now, returning placeholder data structure

        return CustomerSatisfactionDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
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

    private CustomerMetrics calculateCustomerMetrics(Customer customer, Long restaurantId) {
        List<Order> customerOrders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.DELIVERED)
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
                .map(Order::getCreatedAt)
                .min(LocalDateTime::compareTo)
                .orElse(customer.getCreatedAt());

        LocalDateTime lastOrderDate = customerOrders.stream()
                .map(Order::getCreatedAt)
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
