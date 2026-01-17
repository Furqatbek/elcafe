package com.elcafe.modules.ownerbot.scheduler;

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

    /**
     * Send daily sales reports at 22:00 every day
     */
    @Scheduled(cron = "0 0 22 * * *")
    public void sendDailySalesReports() {
        log.info("Starting daily sales report generation...");

        List<Restaurant> restaurants = restaurantRepository.findAllActiveRestaurants();

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
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        // Get order statistics
        List<Object[]> orderStats = orderRepository.getDailyStats(
                restaurant.getId(), startOfDay, endOfDay);

        int totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        if (!orderStats.isEmpty() && orderStats.get(0) != null) {
            Object[] stats = orderStats.get(0);
            totalOrders = ((Number) stats[0]).intValue();
            totalRevenue = stats[1] != null ? (BigDecimal) stats[1] : BigDecimal.ZERO;
        }

        // Get reservation count
        int reservations = reservationRepository.countByRestaurantIdAndDate(
                restaurant.getId(), today);

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
