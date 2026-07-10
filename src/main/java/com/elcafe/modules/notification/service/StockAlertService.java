package com.elcafe.modules.notification.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
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
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final com.elcafe.modules.ownerbot.service.OwnerTelegramBotService ownerBotService;
    private final InventoryIngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final StockAlertSubscriptionRepository subscriptionRepository;
    private final RestaurantRepository restaurantRepository;

    // Default alert threshold for expiring batches (days before expiry)
    private static final int DEFAULT_EXPIRY_ALERT_DAYS = 7;

    /**
     * Scheduled task to check stock levels and send alerts
     * Runs based on configuration (default: every 30 minutes)
     */
    @Scheduled(fixedRateString = "#{${stock-alert.check-interval-minutes:30} * 60000}", initialDelayString = "60000")
    @SchedulerLock(name = "stock-alerts-check", lockAtLeastFor = "PT30S")
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
                boolean sent = ownerBotService.sendStockAlert(
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
                boolean sent = ownerBotService.sendStockAlert(
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
            subscriptionRepository.findByRestaurant_IdAndActiveTrue(restaurantId);

        if (subscriptions.isEmpty()) {
            log.info("No active subscriptions for restaurant: {}", restaurant.getName());
            return;
        }

        List<Ingredient> lowStockItems = ingredientRepository.findLowStockIngredients(restaurantId);
        List<Ingredient> reorderItems = ingredientRepository.findIngredientsNeedingReorder(restaurantId);

        for (StockAlertSubscription subscription : subscriptions) {
            if (subscription.getAlertOnLowStock() && !lowStockItems.isEmpty()) {
                String itemsList = formatIngredientsList(lowStockItems);
                ownerBotService.sendStockAlert(
                    subscription.getTelegramChatId(),
                    restaurant.getName(),
                    "LOW_STOCK",
                    itemsList
                );
            }

            if (subscription.getAlertOnReorder() && !reorderItems.isEmpty()) {
                String itemsList = formatIngredientsList(reorderItems);
                ownerBotService.sendStockAlert(
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
        List<StockAlertSubscription> subscriptions = subscriptionRepository.findByRestaurant_Id(restaurantId);

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

    // ==================== PRODUCTION FEATURE: EXPIRY ALERTS ====================

    /**
     * Scheduled task to check batch expiry and send alerts.
     * Runs daily to alert about expiring and expired batches.
     */
    @Scheduled(cron = "0 0 6 * * *") // Run daily at 6 AM
    @SchedulerLock(name = "stock-expiry-alerts-daily", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    @Transactional
    public void checkAndSendExpiryAlerts() {
        if (!alertConfig.isEnabled()) {
            log.debug("Expiry alerts are disabled (stock alerts disabled)");
            return;
        }

        log.info("Starting batch expiry alert check...");

        List<Restaurant> restaurants = restaurantRepository.findByActiveTrue();

        for (Restaurant restaurant : restaurants) {
            checkRestaurantBatchExpiry(restaurant);
        }

        log.info("Batch expiry alert check completed");
    }

    /**
     * Check batch expiry for a specific restaurant and send alerts.
     */
    private void checkRestaurantBatchExpiry(Restaurant restaurant) {
        LocalDate today = LocalDate.now();
        LocalDate expiryThreshold = today.plusDays(DEFAULT_EXPIRY_ALERT_DAYS);

        // Get expired batches
        List<InventoryBatch> expiredBatches = batchRepository.findExpiredBatches(restaurant.getId(), today);

        // Get expiring batches (within threshold)
        List<InventoryBatch> expiringBatches = batchRepository.findExpiringBatches(
                restaurant.getId(), expiryThreshold);

        // Filter out already expired from expiring list
        expiringBatches = expiringBatches.stream()
                .filter(b -> b.getExpiryDate() != null && !b.getExpiryDate().isBefore(today))
                .collect(Collectors.toList());

        if (expiredBatches.isEmpty() && expiringBatches.isEmpty()) {
            return;
        }

        // Get active subscriptions for this restaurant
        List<StockAlertSubscription> subscriptions =
            subscriptionRepository.findByRestaurant_IdAndActiveTrue(restaurant.getId());

        if (subscriptions.isEmpty()) {
            // Log warning even without subscriptions for visibility
            if (!expiredBatches.isEmpty()) {
                log.warn("EXPIRED BATCHES (no subscribers): Restaurant {} has {} expired batches",
                        restaurant.getName(), expiredBatches.size());
            }
            return;
        }

        // Send alerts to subscribers
        for (StockAlertSubscription subscription : subscriptions) {
            // Send expired batch alert (CRITICAL)
            if (!expiredBatches.isEmpty()) {
                String batchList = formatBatchExpiryList(expiredBatches);
                boolean sent = ownerBotService.sendStockAlert(
                    subscription.getTelegramChatId(),
                    restaurant.getName(),
                    "EXPIRED_BATCHES",
                    batchList
                );
                if (sent) {
                    log.warn("EXPIRED BATCH ALERT sent for restaurant {}: {} batches",
                            restaurant.getName(), expiredBatches.size());
                }
            }

            // Send expiring soon alert (WARNING)
            if (!expiringBatches.isEmpty()) {
                String batchList = formatBatchExpiryList(expiringBatches);
                boolean sent = ownerBotService.sendStockAlert(
                    subscription.getTelegramChatId(),
                    restaurant.getName(),
                    "EXPIRING_SOON",
                    batchList
                );
                if (sent) {
                    log.info("EXPIRING SOON alert sent for restaurant {}: {} batches",
                            restaurant.getName(), expiringBatches.size());
                }
            }

            subscription.setLastAlertSentAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
        }
    }

    /**
     * Format batch expiry list for Telegram message.
     */
    private String formatBatchExpiryList(List<InventoryBatch> batches) {
        StringBuilder sb = new StringBuilder();
        LocalDate today = LocalDate.now();

        for (InventoryBatch batch : batches) {
            String ingredientName = batch.getIngredient() != null ?
                    batch.getIngredient().getName() : "Unknown";
            String unit = batch.getIngredient() != null ?
                    batch.getIngredient().getUnit() : "";

            sb.append(String.format("• <b>%s</b>\n", ingredientName));
            sb.append(String.format("  Партия: %s\n", batch.getBatchNumber()));
            sb.append(String.format("  Остаток: %.2f %s\n", batch.getQuantity(), unit));

            if (batch.getExpiryDate() != null) {
                long daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(today, batch.getExpiryDate());
                if (daysUntilExpiry < 0) {
                    sb.append(String.format("  ⚠️ ПРОСРОЧЕНО: %s (на %d дн.)\n",
                            batch.getExpiryDate(), Math.abs(daysUntilExpiry)));
                } else if (daysUntilExpiry == 0) {
                    sb.append(String.format("  ⚠️ ИСТЕКАЕТ СЕГОДНЯ: %s\n", batch.getExpiryDate()));
                } else {
                    sb.append(String.format("  Срок годности: %s (осталось %d дн.)\n",
                            batch.getExpiryDate(), daysUntilExpiry));
                }
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Manually trigger expiry alert check for a specific restaurant.
     */
    @Transactional
    public void triggerExpiryAlertForRestaurant(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new RuntimeException("Restaurant not found: " + restaurantId));

        checkRestaurantBatchExpiry(restaurant);
        log.info("Manual expiry alert triggered for restaurant: {}", restaurant.getName());
    }

    /**
     * Get expiry summary for a restaurant.
     */
    public Map<String, Object> getExpirySummary(Long restaurantId) {
        LocalDate today = LocalDate.now();
        LocalDate expiryThreshold = today.plusDays(DEFAULT_EXPIRY_ALERT_DAYS);

        List<InventoryBatch> expiredBatches = batchRepository.findExpiredBatches(restaurantId, today);
        List<InventoryBatch> expiringBatches = batchRepository.findExpiringBatches(restaurantId, expiryThreshold);

        // Filter out already expired from expiring list
        expiringBatches = expiringBatches.stream()
                .filter(b -> b.getExpiryDate() != null && !b.getExpiryDate().isBefore(today))
                .collect(Collectors.toList());

        return Map.of(
            "expiredCount", expiredBatches.size(),
            "expiringCount", expiringBatches.size(),
            "expiredBatches", expiredBatches.stream()
                .map(b -> Map.of(
                    "id", b.getId(),
                    "batchNumber", b.getBatchNumber(),
                    "ingredientName", b.getIngredient() != null ? b.getIngredient().getName() : "Unknown",
                    "quantity", b.getQuantity(),
                    "expiryDate", b.getExpiryDate() != null ? b.getExpiryDate().toString() : null
                ))
                .collect(Collectors.toList()),
            "expiringBatches", expiringBatches.stream()
                .map(b -> Map.of(
                    "id", b.getId(),
                    "batchNumber", b.getBatchNumber(),
                    "ingredientName", b.getIngredient() != null ? b.getIngredient().getName() : "Unknown",
                    "quantity", b.getQuantity(),
                    "expiryDate", b.getExpiryDate() != null ? b.getExpiryDate().toString() : null,
                    "daysUntilExpiry", b.getExpiryDate() != null ?
                            java.time.temporal.ChronoUnit.DAYS.between(today, b.getExpiryDate()) : null
                ))
                .collect(Collectors.toList())
        );
    }
}
