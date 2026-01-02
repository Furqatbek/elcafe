package com.elcafe.modules.notification.controller;

import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.notification.dto.FinancialAlertSubscriptionRequest;
import com.elcafe.modules.notification.dto.FinancialAlertSubscriptionResponse;
import com.elcafe.modules.notification.entity.FinancialAlertSubscription;
import com.elcafe.modules.notification.repository.FinancialAlertSubscriptionRepository;
import com.elcafe.modules.notification.service.DailyFinancialReportService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications/financial-alerts")
@RequiredArgsConstructor
public class FinancialAlertController {

    private final FinancialAlertSubscriptionRepository subscriptionRepository;
    private final DailyFinancialReportService dailyFinancialReportService;
    private final RestaurantRepository restaurantRepository;
    private final ShiftTimeService shiftTimeService;

    /**
     * Get all subscriptions for a restaurant
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<FinancialAlertSubscriptionResponse>> getSubscriptions(
            @PathVariable Long restaurantId) {

        List<FinancialAlertSubscription> subscriptions =
            subscriptionRepository.findByRestaurant_Id(restaurantId);

        List<FinancialAlertSubscriptionResponse> responses = subscriptions.stream()
            .map(FinancialAlertSubscriptionResponse::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Create a new financial alert subscription
     */
    @PostMapping
    public ResponseEntity<FinancialAlertSubscriptionResponse> createSubscription(
            @Valid @RequestBody FinancialAlertSubscriptionRequest request) {

        // Check if subscription already exists
        if (subscriptionRepository.findByRestaurant_IdAndTelegramChatId(
                request.getRestaurantId(), request.getTelegramChatId()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
            .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        FinancialAlertSubscription subscription = FinancialAlertSubscription.builder()
            .restaurant(restaurant)
            .telegramChatId(request.getTelegramChatId())
            .subscriberName(request.getSubscriberName())
            .alertDailyRevenue(request.getAlertDailyRevenue())
            .alertDailyExpenses(request.getAlertDailyExpenses())
            .alertDailyProfit(request.getAlertDailyProfit())
            .reportTime(request.getReportTime() != null ? request.getReportTime() : LocalTime.of(23, 0))
            .active(request.getActive())
            .build();

        subscription = subscriptionRepository.save(subscription);
        log.info("Created financial alert subscription for restaurant {} with chatId {}",
            restaurant.getName(), request.getTelegramChatId());

        return ResponseEntity.ok(FinancialAlertSubscriptionResponse.fromEntity(subscription));
    }

    /**
     * Update a subscription
     */
    @PutMapping("/{id}")
    public ResponseEntity<FinancialAlertSubscriptionResponse> updateSubscription(
            @PathVariable Long id,
            @Valid @RequestBody FinancialAlertSubscriptionRequest request) {

        FinancialAlertSubscription subscription = subscriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Subscription not found"));

        subscription.setSubscriberName(request.getSubscriberName());
        subscription.setAlertDailyRevenue(request.getAlertDailyRevenue());
        subscription.setAlertDailyExpenses(request.getAlertDailyExpenses());
        subscription.setAlertDailyProfit(request.getAlertDailyProfit());
        if (request.getReportTime() != null) {
            subscription.setReportTime(request.getReportTime());
        }
        subscription.setActive(request.getActive());

        subscription = subscriptionRepository.save(subscription);
        log.info("Updated financial alert subscription {}", id);

        return ResponseEntity.ok(FinancialAlertSubscriptionResponse.fromEntity(subscription));
    }

    /**
     * Delete a subscription
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubscription(@PathVariable Long id) {
        if (!subscriptionRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        subscriptionRepository.deleteById(id);
        log.info("Deleted financial alert subscription {}", id);

        return ResponseEntity.ok().build();
    }

    /**
     * Toggle subscription active status
     */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<FinancialAlertSubscriptionResponse> toggleSubscription(@PathVariable Long id) {
        FinancialAlertSubscription subscription = subscriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Subscription not found"));

        subscription.setActive(!subscription.getActive());
        subscription = subscriptionRepository.save(subscription);

        log.info("Toggled financial alert subscription {} to active={}",
            id, subscription.getActive());

        return ResponseEntity.ok(FinancialAlertSubscriptionResponse.fromEntity(subscription));
    }

    /**
     * Manually trigger a daily report for a restaurant
     */
    @PostMapping("/trigger/{restaurantId}")
    public ResponseEntity<Map<String, Object>> triggerDailyReport(@PathVariable Long restaurantId) {
        dailyFinancialReportService.triggerReportForRestaurant(restaurantId);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Daily financial report triggered successfully"
        ));
    }

    /**
     * Get daily metrics summary for a restaurant.
     * Uses the same date range parameters as the P&L report API.
     * If no date parameters provided, uses current shift's date range automatically.
     */
    @GetMapping("/metrics/{restaurantId}")
    public ResponseEntity<Map<String, Object>> getDailyMetrics(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // Support both single date and date range (like P&L API)
        LocalDate start;
        LocalDate end;

        if (startDate != null && endDate != null) {
            // Use P&L style date range
            start = startDate;
            end = endDate;
        } else if (date != null) {
            // Single date parameter
            start = date;
            end = date;
        } else {
            // Default to current shift's date range
            // Get the shift time range for today - this handles shifts that cross midnight
            ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRange(restaurantId, LocalDate.now());
            start = shift.start().toLocalDate();
            end = shift.end().toLocalDate();
            log.info("Using current shift date range: {} to {} for restaurant {}", start, end, restaurantId);
        }

        Map<String, Object> metrics = dailyFinancialReportService.getDailyMetricsSummary(restaurantId, start, end);

        return ResponseEntity.ok(metrics);
    }
}
