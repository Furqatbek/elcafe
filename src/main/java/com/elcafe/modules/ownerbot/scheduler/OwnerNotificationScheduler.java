package com.elcafe.modules.ownerbot.scheduler;

import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.reservation.repository.ReservationRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Scheduler for sending periodic notifications to restaurant owners.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerNotificationScheduler {

    private final OwnerNotificationService notificationService;
    private final RestaurantRepository restaurantRepository;
    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final ShiftTimeService shiftTimeService;

    /**
     * Send daily sales reports at 22:00 every day
     */
    @Scheduled(cron = "0 0 22 * * *")
    public void sendDailySalesReports() {
        log.info("Starting daily sales report generation...");

        List<Restaurant> restaurants = restaurantRepository.findByActiveTrue();

        for (Restaurant restaurant : restaurants) {
            try {
                sendDailyReportForRestaurant(restaurant);
            } catch (Exception e) {
                log.error("Failed to send daily report for restaurant {}: {}",
                        restaurant.getName(), e.getMessage());
            }
        }

        log.info("Daily sales reports sent for {} restaurants", restaurants.size());
    }

    private void sendDailyReportForRestaurant(Restaurant restaurant) {
        // Use shift-aware time range for the current business day
        LocalDate currentBusinessDay = shiftTimeService.getCurrentBusinessDay(restaurant.getId());
        ShiftTimeService.ShiftTimeRange shiftRange = shiftTimeService.getShiftTimeRange(
                restaurant.getId(), currentBusinessDay);

        // Get order statistics using shift time range
        Object[] orderStats = orderRepository.getDailyStatsForRestaurant(
                restaurant.getId(), shiftRange.start(), shiftRange.end());

        int totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        if (orderStats != null && orderStats.length >= 2) {
            totalOrders = ((Number) orderStats[0]).intValue();
            totalRevenue = orderStats[1] != null ? (BigDecimal) orderStats[1] : BigDecimal.ZERO;
        }

        // Get reservation count using shift-aware query
        int reservations = reservationRepository.countByRestaurantIdAndShiftTimeRange(
                restaurant.getId(),
                shiftRange.start().toLocalDate(),
                shiftRange.start().toLocalTime(),
                shiftRange.end().toLocalDate(),
                shiftRange.end().toLocalTime());

        // Calculate average order value
        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Send report
        notificationService.sendDailySalesReport(
                restaurant.getId(),
                restaurant.getName(),
                totalOrders,
                totalRevenue,
                reservations,
                avgOrderValue
        );
    }
}
