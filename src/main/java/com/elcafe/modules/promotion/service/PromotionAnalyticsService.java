package com.elcafe.modules.promotion.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.entity.PromotionUsage;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import com.elcafe.modules.promotion.repository.PromotionUsageRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionAnalyticsService {

    private final OrderRepository orderRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;

    /**
     * Get comprehensive discount analytics for a restaurant
     */
    public DiscountAnalytics getDiscountAnalytics(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating discount analytics for restaurant {} from {} to {}", restaurantId, startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // Get all completed orders in the period
        List<Order> allOrders = orderRepository.findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
                restaurantId, startDateTime, endDateTime);

        List<Order> completedOrders = allOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .filter(o -> o.isFullyPaid() || o.getPaymentStatus() == PaymentStatus.COMPLETED)
                .collect(Collectors.toList());

        // Orders with discounts
        List<Order> discountedOrders = completedOrders.stream()
                .filter(o -> o.getDiscount() != null && o.getDiscount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        // Calculate totals
        BigDecimal totalRevenue = completedOrders.stream()
                .map(Order::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grossRevenue = completedOrders.stream()
                .map(Order::getSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscounts = discountedOrders.stream()
                .map(Order::getDiscount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Discount breakdown by type
        Map<String, BigDecimal> discountByType = discountedOrders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getDiscountType() != null ? o.getDiscountType() : "UNKNOWN",
                        Collectors.reducing(BigDecimal.ZERO, Order::getDiscount, BigDecimal::add)
                ));

        // Count by type
        Map<String, Long> orderCountByDiscountType = discountedOrders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getDiscountType() != null ? o.getDiscountType() : "UNKNOWN",
                        Collectors.counting()
                ));

        // Calculate averages
        BigDecimal avgOrderValue = completedOrders.isEmpty() ? BigDecimal.ZERO :
                totalRevenue.divide(BigDecimal.valueOf(completedOrders.size()), 2, RoundingMode.HALF_UP);

        BigDecimal avgDiscountedOrderValue = discountedOrders.isEmpty() ? BigDecimal.ZERO :
                discountedOrders.stream()
                        .map(Order::getTotal)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(discountedOrders.size()), 2, RoundingMode.HALF_UP);

        BigDecimal avgDiscountAmount = discountedOrders.isEmpty() ? BigDecimal.ZERO :
                totalDiscounts.divide(BigDecimal.valueOf(discountedOrders.size()), 2, RoundingMode.HALF_UP);

        // Discount rate (% of orders with discounts)
        BigDecimal discountRate = completedOrders.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(discountedOrders.size())
                        .divide(BigDecimal.valueOf(completedOrders.size()), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        // Discount as percentage of gross revenue
        BigDecimal discountPercentOfRevenue = grossRevenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                totalDiscounts.divide(grossRevenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        return DiscountAnalytics.builder()
                .restaurantId(restaurantId)
                .startDate(startDate)
                .endDate(endDate)
                .totalOrders(completedOrders.size())
                .discountedOrders(discountedOrders.size())
                .grossRevenue(grossRevenue)
                .totalDiscounts(totalDiscounts)
                .netRevenue(totalRevenue)
                .discountByType(discountByType)
                .orderCountByDiscountType(orderCountByDiscountType)
                .avgOrderValue(avgOrderValue)
                .avgDiscountedOrderValue(avgDiscountedOrderValue)
                .avgDiscountAmount(avgDiscountAmount)
                .discountRate(discountRate)
                .discountPercentOfRevenue(discountPercentOfRevenue)
                .build();
    }

    /**
     * Get analytics for a specific promotion
     */
    public PromotionPerformance getPromotionPerformance(Long promotionId) {
        log.info("Getting performance for promotion {}", promotionId);

        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new IllegalArgumentException("Promotion not found: " + promotionId));

        // Get all usage records
        List<PromotionUsage> usages = promotionUsageRepository.findByPromotion_Id(promotionId);

        // Calculate metrics
        int totalRedemptions = usages.size();
        BigDecimal totalDiscountGiven = usages.stream()
                .map(PromotionUsage::getDiscountAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get orders that used this promotion
        Set<Long> orderIds = usages.stream()
                .map(u -> u.getOrder().getId())
                .collect(Collectors.toSet());

        BigDecimal totalRevenueGenerated = BigDecimal.ZERO;
        BigDecimal totalOrderValue = BigDecimal.ZERO;
        int uniqueCustomers = 0;

        if (!orderIds.isEmpty()) {
            List<Order> orders = orderRepository.findAllById(orderIds);
            totalRevenueGenerated = orders.stream()
                    .map(Order::getTotal)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalOrderValue = orders.stream()
                    .map(Order::getSubtotal)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            uniqueCustomers = (int) usages.stream()
                    .filter(u -> u.getCustomer() != null)
                    .map(u -> u.getCustomer().getId())
                    .distinct()
                    .count();
        }

        // Calculate ROI: (Revenue Generated - Discount Cost) / Discount Cost
        BigDecimal roi = totalDiscountGiven.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                totalRevenueGenerated.subtract(totalDiscountGiven)
                        .divide(totalDiscountGiven, 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        BigDecimal avgOrderValue = totalRedemptions == 0 ? BigDecimal.ZERO :
                totalOrderValue.divide(BigDecimal.valueOf(totalRedemptions), 2, RoundingMode.HALF_UP);

        BigDecimal avgDiscountPerRedemption = totalRedemptions == 0 ? BigDecimal.ZERO :
                totalDiscountGiven.divide(BigDecimal.valueOf(totalRedemptions), 2, RoundingMode.HALF_UP);

        return PromotionPerformance.builder()
                .promotionId(promotionId)
                .promotionName(promotion.getName())
                .promotionType(promotion.getPromotionType().name())
                .isActive(promotion.getActive())
                .totalRedemptions(totalRedemptions)
                .uniqueCustomers(uniqueCustomers)
                .totalDiscountGiven(totalDiscountGiven)
                .totalRevenueGenerated(totalRevenueGenerated)
                .avgOrderValue(avgOrderValue)
                .avgDiscountPerRedemption(avgDiscountPerRedemption)
                .roi(roi)
                .build();
    }

    /**
     * Get all promotions performance summary for a restaurant
     */
    public List<PromotionPerformance> getAllPromotionsPerformance(Long restaurantId) {
        log.info("Getting all promotions performance for restaurant {}", restaurantId);

        List<Promotion> promotions = promotionRepository.findByRestaurant_IdOrderByPriorityDescCreatedAtDesc(restaurantId);

        return promotions.stream()
                .map(p -> getPromotionPerformance(p.getId()))
                .sorted((a, b) -> b.getTotalRevenueGenerated().compareTo(a.getTotalRevenueGenerated()))
                .collect(Collectors.toList());
    }

    /**
     * Get discount trends over time (daily breakdown)
     */
    public List<DailyDiscountTrend> getDiscountTrends(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting discount trends for restaurant {} from {} to {}", restaurantId, startDate, endDate);

        List<DailyDiscountTrend> trends = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            LocalDateTime dayStart = currentDate.atStartOfDay();
            LocalDateTime dayEnd = currentDate.atTime(LocalTime.MAX);

            List<Order> dayOrders = orderRepository.findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    restaurantId, dayStart, dayEnd);

            List<Order> completedOrders = dayOrders.stream()
                    .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                    .filter(o -> o.isFullyPaid() || o.getPaymentStatus() == PaymentStatus.COMPLETED)
                    .collect(Collectors.toList());

            List<Order> discountedOrders = completedOrders.stream()
                    .filter(o -> o.getDiscount() != null && o.getDiscount().compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toList());

            BigDecimal dayRevenue = completedOrders.stream()
                    .map(Order::getTotal)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal dayDiscounts = discountedOrders.stream()
                    .map(Order::getDiscount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            trends.add(DailyDiscountTrend.builder()
                    .date(currentDate)
                    .totalOrders(completedOrders.size())
                    .discountedOrders(discountedOrders.size())
                    .totalRevenue(dayRevenue)
                    .totalDiscounts(dayDiscounts)
                    .build());

            currentDate = currentDate.plusDays(1);
        }

        return trends;
    }

    /**
     * Get top performing coupons by revenue generated
     */
    public List<CouponPerformance> getTopCoupons(Long restaurantId, LocalDate startDate, LocalDate endDate, int limit) {
        log.info("Getting top coupons for restaurant {} from {} to {}", restaurantId, startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Order> ordersWithCoupons = orderRepository.findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        restaurantId, startDateTime, endDateTime).stream()
                .filter(o -> o.getCouponCode() != null && !o.getCouponCode().isEmpty())
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        // Group by coupon code
        Map<String, List<Order>> ordersByCoupon = ordersWithCoupons.stream()
                .collect(Collectors.groupingBy(Order::getCouponCode));

        List<CouponPerformance> performances = new ArrayList<>();
        for (Map.Entry<String, List<Order>> entry : ordersByCoupon.entrySet()) {
            String couponCode = entry.getKey();
            List<Order> orders = entry.getValue();

            BigDecimal totalRevenue = orders.stream()
                    .map(Order::getTotal)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalDiscount = orders.stream()
                    .map(Order::getDiscount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            performances.add(CouponPerformance.builder()
                    .couponCode(couponCode)
                    .redemptionCount(orders.size())
                    .totalRevenue(totalRevenue)
                    .totalDiscount(totalDiscount)
                    .avgOrderValue(orders.isEmpty() ? BigDecimal.ZERO :
                            totalRevenue.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP))
                    .build());
        }

        return performances.stream()
                .sorted((a, b) -> b.getTotalRevenue().compareTo(a.getTotalRevenue()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // DTO Classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscountAnalytics {
        private Long restaurantId;
        private LocalDate startDate;
        private LocalDate endDate;
        private int totalOrders;
        private int discountedOrders;
        private BigDecimal grossRevenue;
        private BigDecimal totalDiscounts;
        private BigDecimal netRevenue;
        private Map<String, BigDecimal> discountByType;
        private Map<String, Long> orderCountByDiscountType;
        private BigDecimal avgOrderValue;
        private BigDecimal avgDiscountedOrderValue;
        private BigDecimal avgDiscountAmount;
        private BigDecimal discountRate;  // % of orders with discounts
        private BigDecimal discountPercentOfRevenue;  // discounts as % of gross revenue
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionPerformance {
        private Long promotionId;
        private String promotionName;
        private String promotionType;
        private Boolean isActive;
        private int totalRedemptions;
        private int uniqueCustomers;
        private BigDecimal totalDiscountGiven;
        private BigDecimal totalRevenueGenerated;
        private BigDecimal avgOrderValue;
        private BigDecimal avgDiscountPerRedemption;
        private BigDecimal roi;  // Return on Investment %
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyDiscountTrend {
        private LocalDate date;
        private int totalOrders;
        private int discountedOrders;
        private BigDecimal totalRevenue;
        private BigDecimal totalDiscounts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponPerformance {
        private String couponCode;
        private int redemptionCount;
        private BigDecimal totalRevenue;
        private BigDecimal totalDiscount;
        private BigDecimal avgOrderValue;
    }
}
