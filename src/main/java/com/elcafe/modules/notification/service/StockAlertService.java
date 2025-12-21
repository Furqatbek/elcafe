package com.elcafe.modules.notification.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.notification.config.StockAlertConfig;
import com.elcafe.modules.notification.entity.StockAlertSubscription;
import com.elcafe.modules.notification.repository.StockAlertSubscriptionRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for checking stock levels and sending alerts via Telegram
 */
@Slf4j
@Service
@Lazy
@RequiredArgsConstructor
public class StockAlertService {

    private final StockAlertConfig alertConfig;
    private final TelegramBotService telegramBotService;
    private final InventoryIngredientRepository ingredientRepository;
    private final StockAlertSubscriptionRepository subscriptionRepository;
    private final RestaurantRepository restaurantRepository;

    /**
     * Scheduled task to check stock levels and send alerts
     * Runs based on configuration (default: every 30 minutes)
     */
    @Scheduled(fixedRateString = "#{${stock-alert.check-interval-minutes:30} * 60000}", initialDelayString = "60000")
    @Transactional
    public void checkAndSendAlerts() {
        if (!alertConfig.isEnabled()) {
            log.debug("Stock alerts are disabled");
            return;
        }

        log.info("Starting stock alert check...");

        // Get all active restaurants
        List<Restaurant> restaurants = restaurantRepository.findByActiveTrue();

        for (Restaurant restaurant : restaurants) {
            checkRestaurantStock(restaurant);
        }

        log.info("Stock alert check completed");
    }

    /**
     * Check stock for a specific restaurant and send alerts
     */
    private void checkRestaurantStock(Restaurant restaurant) {
        LocalDateTime cooldownTime = LocalDateTime.now().minusHours(alertConfig.getAlertCooldownHours());

        // Get eligible subscriptions (active and not in cooldown)
        List<StockAlertSubscription> subscriptions =
            subscriptionRepository.findEligibleForAlert(restaurant.getId(), cooldownTime);

        if (subscriptions.isEmpty()) {
            return;
        }

        // Get low stock ingredients
        List<Ingredient> lowStockItems = ingredientRepository.findLowStockIngredients(restaurant.getId());
        List<Ingredient> reorderItems = ingredientRepository.findIngredientsNeedingReorder(restaurant.getId());

        // Filter reorder items that are not already in low stock
        List<Ingredient> reorderOnlyItems = reorderItems.stream()
            .filter(item -> lowStockItems.stream().noneMatch(ls -> ls.getId().equals(item.getId())))
            .collect(Collectors.toList());

        if (lowStockItems.isEmpty() && reorderOnlyItems.isEmpty()) {
            return;
        }

        // Send alerts to each subscriber
        for (StockAlertSubscription subscription : subscriptions) {
            boolean alertSent = false;

            // Send low stock alert
            if (subscription.getAlertOnLowStock() && !lowStockItems.isEmpty()) {
                String itemsList = formatIngredientsList(lowStockItems);
                boolean sent = telegramBotService.sendStockAlert(
                    subscription.getTelegramChatId(),
                    restaurant.getName(),
                    "LOW_STOCK",
                    itemsList
                );
                if (sent) alertSent = true;
            }

            // Send reorder alert
            if (subscription.getAlertOnReorder() && !reorderOnlyItems.isEmpty()) {
                String itemsList = formatIngredientsList(reorderOnlyItems);
                boolean sent = telegramBotService.sendStockAlert(
                    subscription.getTelegramChatId(),
                    restaurant.getName(),
                    "REORDER",
                    itemsList
                );
                if (sent) alertSent = true;
            }

            // Update last alert time if any alert was sent
            if (alertSent) {
                subscription.setLastAlertSentAt(LocalDateTime.now());
                subscriptionRepository.save(subscription);
                log.info("Stock alert sent to chatId {} for restaurant {}",
                    subscription.getTelegramChatId(), restaurant.getName());
            }
        }
    }

    /**
     * Format ingredients list for Telegram message
     */
    private String formatIngredientsList(List<Ingredient> ingredients) {
        StringBuilder sb = new StringBuilder();
        for (Ingredient ing : ingredients) {
            sb.append(String.format("• <b>%s</b>\n", ing.getName()));
            sb.append(String.format("  Остаток: %.2f %s\n", ing.getCurrentStock(), ing.getUnit()));
            sb.append(String.format("  Минимум: %.2f %s\n", ing.getMinimumStock(), ing.getUnit()));
            if (ing.getSupplier() != null && !ing.getSupplier().isEmpty()) {
                sb.append(String.format("  Поставщик: %s\n", ing.getSupplier()));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Manually trigger stock check for a specific restaurant
     */
    @Transactional
    public void triggerAlertForRestaurant(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new RuntimeException("Restaurant not found: " + restaurantId));

        // Temporarily bypass cooldown for manual trigger
        List<StockAlertSubscription> subscriptions =
            subscriptionRepository.findByRestaurantIdAndActiveTrue(restaurantId);

        if (subscriptions.isEmpty()) {
            log.info("No active subscriptions for restaurant: {}", restaurant.getName());
            return;
        }

        List<Ingredient> lowStockItems = ingredientRepository.findLowStockIngredients(restaurantId);
        List<Ingredient> reorderItems = ingredientRepository.findIngredientsNeedingReorder(restaurantId);

        for (StockAlertSubscription subscription : subscriptions) {
            if (subscription.getAlertOnLowStock() && !lowStockItems.isEmpty()) {
                String itemsList = formatIngredientsList(lowStockItems);
                telegramBotService.sendStockAlert(
                    subscription.getTelegramChatId(),
                    restaurant.getName(),
                    "LOW_STOCK",
                    itemsList
                );
            }

            if (subscription.getAlertOnReorder() && !reorderItems.isEmpty()) {
                String itemsList = formatIngredientsList(reorderItems);
                telegramBotService.sendStockAlert(
                    subscription.getTelegramChatId(),
                    restaurant.getName(),
                    "REORDER",
                    itemsList
                );
            }

            subscription.setLastAlertSentAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
        }

        log.info("Manual stock alert triggered for restaurant: {}", restaurant.getName());
    }

    /**
     * Get stock summary for a restaurant
     */
    public Map<String, Object> getStockSummary(Long restaurantId) {
        List<Ingredient> lowStockItems = ingredientRepository.findLowStockIngredients(restaurantId);
        List<Ingredient> reorderItems = ingredientRepository.findIngredientsNeedingReorder(restaurantId);
        List<StockAlertSubscription> subscriptions = subscriptionRepository.findByRestaurantId(restaurantId);

        return Map.of(
            "lowStockCount", lowStockItems.size(),
            "reorderCount", reorderItems.size(),
            "activeSubscriptions", subscriptions.stream().filter(StockAlertSubscription::getActive).count(),
            "lowStockItems", lowStockItems.stream()
                .map(i -> Map.of(
                    "id", i.getId(),
                    "name", i.getName(),
                    "currentStock", i.getCurrentStock(),
                    "minimumStock", i.getMinimumStock(),
                    "unit", i.getUnit()
                ))
                .collect(Collectors.toList())
        );
    }
}
