package com.elcafe.modules.ownerbot.scheduler;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    private void checkLowStockForRestaurant(Restaurant restaurant) {
        List<Ingredient> lowStockItems = ingredientRepository.findLowStockIngredients(restaurant.getId());

        for (Ingredient ingredient : lowStockItems) {
            // Only alert if below threshold
            BigDecimal threshold = ingredient.getMinimumStock() != null
                    ? ingredient.getMinimumStock()
                    : BigDecimal.TEN;

            if (ingredient.getCurrentStock().compareTo(threshold) < 0) {
                notificationService.notifyLowStock(
                        restaurant.getId(),
                        ingredient.getName(),
                        ingredient.getCurrentStock().intValue(),
                        threshold.intValue()
                );
            }
        }

        if (!lowStockItems.isEmpty()) {
            log.info("Low stock alerts sent for {} items in restaurant {}",
                    lowStockItems.size(), restaurant.getName());
        }
    }
}
