package com.elcafe.modules.notification.controller;

import com.elcafe.modules.notification.dto.StockAlertSubscriptionRequest;
import com.elcafe.modules.notification.dto.StockAlertSubscriptionResponse;
import com.elcafe.modules.notification.entity.StockAlertSubscription;
import com.elcafe.modules.notification.repository.StockAlertSubscriptionRepository;
import com.elcafe.modules.notification.service.StockAlertService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for managing stock alert subscriptions
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/stock-alerts")
@RequiredArgsConstructor
@Tag(name = "Stock Alerts", description = "Stock alert subscription management")
public class StockAlertController {

    private final StockAlertSubscriptionRepository subscriptionRepository;
    private final StockAlertService stockAlertService;
    private final RestaurantRepository restaurantRepository;

    @GetMapping("/subscriptions")
    @Operation(summary = "Get all subscriptions", description = "Get all stock alert subscriptions")
    public ResponseEntity<ApiResponse<List<StockAlertSubscriptionResponse>>> getAllSubscriptions(
            @RequestParam(required = false) Long restaurantId) {

        List<StockAlertSubscription> subscriptions;
        if (restaurantId != null) {
            subscriptions = subscriptionRepository.findByRestaurantId(restaurantId);
        } else {
            subscriptions = subscriptionRepository.findAll();
        }

        List<StockAlertSubscriptionResponse> responses = subscriptions.stream()
            .map(StockAlertSubscriptionResponse::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Subscriptions retrieved successfully", responses));
    }

    @GetMapping("/subscriptions/{id}")
    @Operation(summary = "Get subscription by ID", description = "Get a specific stock alert subscription")
    public ResponseEntity<ApiResponse<StockAlertSubscriptionResponse>> getSubscription(@PathVariable Long id) {
        StockAlertSubscription subscription = subscriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Subscription not found: " + id));

        return ResponseEntity.ok(ApiResponse.success(
            "Subscription retrieved successfully",
            StockAlertSubscriptionResponse.fromEntity(subscription)
        ));
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Create subscription", description = "Create a new stock alert subscription")
    public ResponseEntity<ApiResponse<StockAlertSubscriptionResponse>> createSubscription(
            @Valid @RequestBody StockAlertSubscriptionRequest request) {

        // Check if subscription already exists
        if (subscriptionRepository.existsByRestaurantIdAndTelegramChatId(
                request.getRestaurantId(), request.getTelegramChatId())) {
            throw new RuntimeException("Subscription already exists for this restaurant and chat ID");
        }

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
            .orElseThrow(() -> new RuntimeException("Restaurant not found: " + request.getRestaurantId()));

        StockAlertSubscription subscription = StockAlertSubscription.builder()
            .restaurant(restaurant)
            .telegramChatId(request.getTelegramChatId())
            .subscriberName(request.getSubscriberName())
            .alertOnLowStock(request.getAlertOnLowStock())
            .alertOnReorder(request.getAlertOnReorder())
            .active(true)
            .build();

        subscription = subscriptionRepository.save(subscription);
        log.info("Created stock alert subscription for restaurant {} chatId {}",
            restaurant.getName(), request.getTelegramChatId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            "Subscription created successfully",
            StockAlertSubscriptionResponse.fromEntity(subscription)
        ));
    }

    @PutMapping("/subscriptions/{id}")
    @Operation(summary = "Update subscription", description = "Update a stock alert subscription")
    public ResponseEntity<ApiResponse<StockAlertSubscriptionResponse>> updateSubscription(
            @PathVariable Long id,
            @Valid @RequestBody StockAlertSubscriptionRequest request) {

        StockAlertSubscription subscription = subscriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Subscription not found: " + id));

        subscription.setSubscriberName(request.getSubscriberName());
        subscription.setAlertOnLowStock(request.getAlertOnLowStock());
        subscription.setAlertOnReorder(request.getAlertOnReorder());

        subscription = subscriptionRepository.save(subscription);

        return ResponseEntity.ok(ApiResponse.success(
            "Subscription updated successfully",
            StockAlertSubscriptionResponse.fromEntity(subscription)
        ));
    }

    @PatchMapping("/subscriptions/{id}/toggle")
    @Operation(summary = "Toggle subscription", description = "Activate or deactivate a subscription")
    public ResponseEntity<ApiResponse<StockAlertSubscriptionResponse>> toggleSubscription(
            @PathVariable Long id) {

        StockAlertSubscription subscription = subscriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Subscription not found: " + id));

        subscription.setActive(!subscription.getActive());
        subscription = subscriptionRepository.save(subscription);

        String status = subscription.getActive() ? "activated" : "deactivated";
        log.info("Subscription {} {}", id, status);

        return ResponseEntity.ok(ApiResponse.success(
            "Subscription " + status + " successfully",
            StockAlertSubscriptionResponse.fromEntity(subscription)
        ));
    }

    @DeleteMapping("/subscriptions/{id}")
    @Operation(summary = "Delete subscription", description = "Delete a stock alert subscription")
    public ResponseEntity<ApiResponse<Void>> deleteSubscription(@PathVariable Long id) {
        if (!subscriptionRepository.existsById(id)) {
            throw new RuntimeException("Subscription not found: " + id);
        }

        subscriptionRepository.deleteById(id);
        log.info("Deleted stock alert subscription: {}", id);

        return ResponseEntity.ok(ApiResponse.success("Subscription deleted successfully", null));
    }

    @PostMapping("/trigger/{restaurantId}")
    @Operation(summary = "Trigger alert", description = "Manually trigger stock alert for a restaurant")
    public ResponseEntity<ApiResponse<Void>> triggerAlert(@PathVariable Long restaurantId) {
        stockAlertService.triggerAlertForRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Stock alert triggered successfully", null));
    }

    @GetMapping("/summary/{restaurantId}")
    @Operation(summary = "Get stock summary", description = "Get stock alert summary for a restaurant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStockSummary(@PathVariable Long restaurantId) {
        Map<String, Object> summary = stockAlertService.getStockSummary(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Stock summary retrieved successfully", summary));
    }
}
