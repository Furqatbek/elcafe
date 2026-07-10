package com.elcafe.modules.ownerbot.scheduler;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduler for checking low stock levels and sending alerts to owners.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LowStockAlertScheduler {

    private final OwnerNotificationService notificationService;
    private final RestaurantRepository restaurantRepository;
    private final InventoryIngredientRepository ingredientRepository;

    /**
     * Check for low stock items every 2 hours during working hours
     */
    @Scheduled(cron = "0 0 8-22/2 * * *")
    @SchedulerLock(name = "owner-low-stock-alerts", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    public void checkLowStockAlerts() {
        log.info("Checking for low stock alerts...");

        List<Restaurant> restaurants = restaurantRepository.findByActiveTrue();

        for (Restaurant restaurant : restaurants) {
            try {
                checkLowStockForRestaurant(restaurant);
            } catch (Exception e) {
                log.error("Failed to check low stock for restaurant {}: {}",
                        restaurant.getName(), e.getMessage());
            }
        }
    }

    public void checkLowStockForRestaurant(Restaurant restaurant) {
        List<Ingredient> lowStockItems = ingredientRepository.findLowStockIngredients(restaurant.getId());

        // Collect all below-threshold items first, then send one batched async notification.
        // Previously one @Async call was submitted per item, causing a task burst that could
        // saturate the executor queue and force tasks to run synchronously on the scheduler thread.
        List<String[]> belowThreshold = new ArrayList<>();
        for (Ingredient ingredient : lowStockItems) {
            BigDecimal threshold = ingredient.getMinimumStock() != null
                    ? ingredient.getMinimumStock()
                    : BigDecimal.TEN;
            if (ingredient.getCurrentStock().compareTo(threshold) < 0) {
                belowThreshold.add(new String[]{
                        ingredient.getName(),
                        String.valueOf(ingredient.getCurrentStock().intValue()),
                        String.valueOf(threshold.intValue())
                });
            }
        }

        if (!belowThreshold.isEmpty()) {
            notificationService.notifyLowStockBatch(restaurant.getId(), belowThreshold);
            log.info("Low stock batch alert sent for {} items in restaurant {}",
                    belowThreshold.size(), restaurant.getName());
        }
    }
}
