package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.*;
import com.elcafe.modules.order.entity.DeliveryInfo;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderStatusHistory;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for operational analytics calculations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationalAnalyticsService {

    private final OrderRepository orderRepository;

    /**
     * Calculate sales per hour
     */
    public List<SalesPerHourDTO> getSalesPerHour(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Order> orders = getCompletedOrders(startDateTime, endDateTime, restaurantId);

        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group by hour
        Map<Integer, List<Order>> ordersByHour = orders.stream()
                .collect(Collectors.groupingBy(order -> order.getCreatedAt().getHour()));

        return ordersByHour.entrySet().stream()
                .map(entry -> {
                    Integer hour = entry.getKey();
                    List<Order> hourlyOrders = entry.getValue();

                    BigDecimal hourRevenue = hourlyOrders.stream()
                            .map(Order::getTotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    int orderCount = hourlyOrders.size();

                    BigDecimal avgOrderValue = orderCount > 0
                            ? hourRevenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    BigDecimal percentageOfDaily = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                            ? hourRevenue.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    return SalesPerHourDTO.builder()
                            .hour(hour)
                            .totalRevenue(hourRevenue)
                            .totalOrders(orderCount)
                            .averageOrderValue(avgOrderValue)
                            .percentageOfDailyRevenue(percentageOfDaily)
                            .build();
                })
                .sorted(Comparator.comparing(SalesPerHourDTO::getHour))
                .collect(Collectors.toList());
    }

    /**
     * Identify peak hours
     */
    public PeakHoursDTO getPeakHours(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        List<SalesPerHourDTO> salesPerHour = getSalesPerHour(startDate, endDate, restaurantId);

        // Calculate average orders per hour
        double avgOrdersPerHour = salesPerHour.stream()
                .mapToInt(SalesPerHourDTO::getTotalOrders)
                .average()
                .orElse(0.0);

        // Peak hours are those with above-average orders
        List<Integer> peakHours = salesPerHour.stream()
                .filter(dto -> dto.getTotalOrders() > avgOrdersPerHour)
                .map(SalesPerHourDTO::getHour)
                .sorted()
                .collect(Collectors.toList());

        // Find peak start and end times
        LocalTime averagePeakStart = peakHours.isEmpty() ? LocalTime.NOON
                : LocalTime.of(peakHours.get(0), 0);

        LocalTime averagePeakEnd = peakHours.isEmpty() ? LocalTime.of(13, 0)
                : LocalTime.of(peakHours.get(peakHours.size() - 1), 59);

        int totalOrdersDuringPeakHours = salesPerHour.stream()
                .filter(dto -> peakHours.contains(dto.getHour()))
                .mapToInt(SalesPerHourDTO::getTotalOrders)
                .sum();

        int totalOrdersOutsidePeakHours = salesPerHour.stream()
                .filter(dto -> !peakHours.contains(dto.getHour()))
                .mapToInt(SalesPerHourDTO::getTotalOrders)
                .sum();

        int totalOrders = totalOrdersDuringPeakHours + totalOrdersOutsidePeakHours;
        double peakHoursPercentage = totalOrders > 0
                ? (double) totalOrdersDuringPeakHours / totalOrders * 100
                : 0.0;

        return PeakHoursDTO.builder()
                .peakHours(peakHours)
                .averagePeakStart(averagePeakStart)
                .averagePeakEnd(averagePeakEnd)
                .totalOrdersDuringPeakHours(totalOrdersDuringPeakHours)
                .totalOrdersOutsidePeakHours(totalOrdersOutsidePeakHours)
                .peakHoursPercentage(peakHoursPercentage)
                .hourlySalesBreakdown(salesPerHour)
                .build();
    }

    /**
     * Calculate table turnover rate
     * Note: This requires table/seat configuration data which should be provided
     */
    public TableTurnoverDTO getTableTurnover(
            LocalDate startDate, LocalDate endDate, Long restaurantId,
            Integer totalTables, Integer totalSeats, Integer operatingHoursPerDay) {

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Order> orders = getCompletedOrders(startDateTime, endDateTime, restaurantId);

        // Count dine-in orders (orders without delivery info)
        long totalDineInOrders = orders.stream()
                .filter(order -> order.getDeliveryInfo() == null)
                .count();

        long daysBetween = Duration.between(startDateTime, endDateTime).toDays() + 1;

        int tables = totalTables != null ? totalTables : 20; // default
        int seats = totalSeats != null ? totalSeats : tables * 4; // default 4 seats per table
        int operatingHours = operatingHoursPerDay != null ? operatingHoursPerDay : 12; // default 12 hours

        double averageTurnoverRate = tables > 0 && daysBetween > 0
                ? (double) totalDineInOrders / tables / daysBetween
                : 0.0;

        double ordersPerSeatPerDay = seats > 0 && daysBetween > 0
                ? (double) totalDineInOrders / seats / daysBetween
                : 0.0;

        // Estimate occupancy (simplified calculation)
        double averageOccupancyRate = averageTurnoverRate > 0
                ? Math.min(averageTurnoverRate / operatingHours * 100, 100.0)
                : 0.0;

        return TableTurnoverDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalTables(tables)
                .totalSeats(seats)
                .totalDineInOrders((int) totalDineInOrders)
                .averageTurnoverRate(averageTurnoverRate)
                .averageOccupancyRate(averageOccupancyRate)
                .totalOperatingHours(operatingHours * (int) daysBetween)
                .ordersPerSeatPerDay(ordersPerSeatPerDay)
                .build();
    }

    /**
     * Calculate order timing analytics (preparation, wait time, delivery)
     */
    public OrderTimingAnalyticsDTO getOrderTimingAnalytics(LocalDate startDate, LocalDate endDate, Long restaurantId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Order> orders = getCompletedOrders(startDateTime, endDateTime, restaurantId);

        // Calculate preparation times (from NEW to PREPARING to READY/COMPLETED)
        List<Double> preparationTimes = orders.stream()
                .map(this::calculatePreparationTime)
                .filter(time -> time > 0)
                .collect(Collectors.toList());

        // Calculate dine-in wait times (from READY to COMPLETED for non-delivery orders)
        List<Double> dineInWaitTimes = orders.stream()
                .filter(order -> order.getDeliveryInfo() == null)
                .map(this::calculateWaitTime)
                .filter(time -> time > 0)
                .collect(Collectors.toList());

        // Calculate delivery times (from READY to DELIVERED for delivery orders)
        List<Double> deliveryTimes = orders.stream()
                .filter(order -> order.getDeliveryInfo() != null)
                .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                .map(this::calculateDeliveryTime)
                .filter(time -> time > 0)
                .collect(Collectors.toList());

        // Calculate statistics
        double avgPreparationTime = calculateAverage(preparationTimes);
        double medianPreparationTime = calculateMedian(preparationTimes);
        double minPreparationTime = preparationTimes.isEmpty() ? 0.0 : Collections.min(preparationTimes);
        double maxPreparationTime = preparationTimes.isEmpty() ? 0.0 : Collections.max(preparationTimes);

        double avgDineInWaitTime = calculateAverage(dineInWaitTimes);
        double medianDineInWaitTime = calculateMedian(dineInWaitTimes);

        double avgDeliveryTime = calculateAverage(deliveryTimes);
        double medianDeliveryTime = calculateMedian(deliveryTimes);
        double minDeliveryTime = deliveryTimes.isEmpty() ? 0.0 : Collections.min(deliveryTimes);
        double maxDeliveryTime = deliveryTimes.isEmpty() ? 0.0 : Collections.max(deliveryTimes);

        // Calculate percentage meeting targets
        long preparationUnder15 = preparationTimes.stream().filter(time -> time <= 15).count();
        double percentagePreparationUnder15Min = preparationTimes.isEmpty() ? 0.0
                : (double) preparationUnder15 / preparationTimes.size() * 100;

        long deliveryUnder30 = deliveryTimes.stream().filter(time -> time <= 30).count();
        double percentageDeliveryUnder30Min = deliveryTimes.isEmpty() ? 0.0
                : (double) deliveryUnder30 / deliveryTimes.size() * 100;

        return OrderTimingAnalyticsDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .averagePreparationTimeMinutes(avgPreparationTime)
                .medianPreparationTimeMinutes(medianPreparationTime)
                .minPreparationTimeMinutes(minPreparationTime)
                .maxPreparationTimeMinutes(maxPreparationTime)
                .averageDineInWaitTimeMinutes(avgDineInWaitTime)
                .medianDineInWaitTimeMinutes(medianDineInWaitTime)
                .averageDeliveryTimeMinutes(avgDeliveryTime)
                .medianDeliveryTimeMinutes(medianDeliveryTime)
                .minDeliveryTimeMinutes(minDeliveryTime)
                .maxDeliveryTimeMinutes(maxDeliveryTime)
                .percentagePreparationUnder15Min(percentagePreparationUnder15Min)
                .percentageDeliveryUnder30Min(percentageDeliveryUnder30Min)
                .totalOrdersAnalyzed(orders.size())
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

    private double calculatePreparationTime(Order order) {
        // Find timestamps from status history
        Optional<LocalDateTime> newTime = findStatusTime(order, OrderStatus.NEW);
        Optional<LocalDateTime> readyTime = findStatusTime(order, OrderStatus.READY);

        if (newTime.isPresent() && readyTime.isPresent()) {
            return Duration.between(newTime.get(), readyTime.get()).toMinutes();
        }

        // Fallback: use creation time to first status change
        if (newTime.isPresent()) {
            return Duration.between(newTime.get(), order.getUpdatedAt()).toMinutes();
        }

        return 0.0;
    }

    private double calculateWaitTime(Order order) {
        Optional<LocalDateTime> readyTime = findStatusTime(order, OrderStatus.READY);
        Optional<LocalDateTime> completedTime = findStatusTime(order, OrderStatus.COMPLETED);

        if (readyTime.isPresent() && completedTime.isPresent()) {
            return Duration.between(readyTime.get(), completedTime.get()).toMinutes();
        }

        return 0.0;
    }

    private double calculateDeliveryTime(Order order) {
        DeliveryInfo deliveryInfo = order.getDeliveryInfo();
        if (deliveryInfo == null) {
            return 0.0;
        }

        Optional<LocalDateTime> readyTime = findStatusTime(order, OrderStatus.READY);

        if (readyTime.isPresent() && deliveryInfo.getActualDeliveryTime() != null) {
            return Duration.between(readyTime.get(), deliveryInfo.getActualDeliveryTime()).toMinutes();
        }

        // Fallback to delivered status time
        Optional<LocalDateTime> deliveredTime = findStatusTime(order, OrderStatus.DELIVERED);
        if (readyTime.isPresent() && deliveredTime.isPresent()) {
            return Duration.between(readyTime.get(), deliveredTime.get()).toMinutes();
        }

        return 0.0;
    }

    private Optional<LocalDateTime> findStatusTime(Order order, OrderStatus status) {
        if (order.getStatusHistory() == null || order.getStatusHistory().isEmpty()) {
            // Fallback to current status
            if (order.getStatus() == status) {
                return Optional.of(order.getCreatedAt());
            }
            return Optional.empty();
        }

        return order.getStatusHistory().stream()
                .filter(history -> history.getStatus() == status)
                .map(OrderStatusHistory::getCreatedAt)
                .min(LocalDateTime::compareTo);
    }

    private double calculateAverage(List<Double> values) {
        return values.isEmpty() ? 0.0 : values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double calculateMedian(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }
}
