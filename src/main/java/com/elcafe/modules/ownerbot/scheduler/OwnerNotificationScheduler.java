package com.elcafe.modules.ownerbot.scheduler;

import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerNotificationScheduler {

    private final OwnerNotificationService notificationService;
    private final RestaurantRepository restaurantRepository;

    @Scheduled(cron = "0 0 22 * * *")
    public void sendDailySalesReports() {
        log.info("Starting daily sales report generation...");

        List<Restaurant> restaurants = restaurantRepository.findByActiveTrue();

        for (Restaurant restaurant : restaurants) {
            try {
                notificationService.sendDailySalesReport(restaurant.getId(), restaurant.getName());
            } catch (Exception e) {
                log.error("Failed to send daily report for restaurant {}: {}",
                        restaurant.getName(), e.getMessage());
            }
        }

        log.info("Daily sales reports sent for {} restaurants", restaurants.size());
    }
}
