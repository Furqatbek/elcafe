package com.elcafe.modules.push.controller;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.security.CustomerPrincipal;
import com.elcafe.modules.customer.service.CustomerService;
import com.elcafe.modules.push.dto.PushNotificationRequest;
import com.elcafe.modules.push.dto.PushSubscriptionRequest;
import com.elcafe.modules.push.dto.VapidKeysResponse;
import com.elcafe.modules.push.entity.PushSubscription;
import com.elcafe.modules.push.enums.PushNotificationType;
import com.elcafe.modules.push.repository.PushSubscriptionRepository;
import com.elcafe.modules.push.service.WebPushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for web push notification subscriptions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
@Tag(name = "Push Notifications", description = "Web Push notification subscription management")
public class PushSubscriptionController {

    private final WebPushService webPushService;
    private final PushSubscriptionRepository subscriptionRepository;
    private final CustomerService customerService;
    private final UserRepository userRepository;

    /**
     * Get VAPID public key for client subscription.
     */
    @GetMapping("/vapid-key")
    @Operation(summary = "Get VAPID public key", description = "Returns the VAPID public key needed for push subscription")
    public ResponseEntity<VapidKeysResponse> getVapidKey() {
        String publicKey = webPushService.getVapidPublicKey();
        if (publicKey == null || publicKey.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new VapidKeysResponse(publicKey));
    }

    /**
     * Subscribe customer to push notifications.
     */
    @PostMapping("/subscribe/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Subscribe customer", description = "Subscribe a customer to receive push notifications")
    public ResponseEntity<Map<String, Object>> subscribeCustomer(
            @Valid @RequestBody PushSubscriptionRequest request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // The consumer token carries the authoritative customer id (CustomerPrincipal) — use it.
        // The previous phone lookup was unscoped (audit MIG-13): the same phone can exist as
        // customer rows in several restaurants after V150, and the single-result lookup threw
        // IncorrectResultSizeDataAccessException (HTTP 500) for exactly those consumers.
        CustomerPrincipal principal = (CustomerPrincipal) auth.getPrincipal();
        Customer customer = customerService.getCustomerById(principal.getId());

        PushSubscription subscription = webPushService.subscribeCustomer(customer, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("subscriptionId", subscription.getId());
        response.put("message", "Successfully subscribed to push notifications");

        log.info("Customer {} subscribed to push notifications", customer.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Subscribe admin user to push notifications.
     */
    @PostMapping("/subscribe/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'COURIER')")
    @Operation(summary = "Subscribe admin user", description = "Subscribe an admin user to receive push notifications")
    public ResponseEntity<Map<String, Object>> subscribeAdmin(
            @Valid @RequestBody PushSubscriptionRequest request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PushSubscription subscription = webPushService.subscribeUser(user, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("subscriptionId", subscription.getId());
        response.put("message", "Successfully subscribed to push notifications");

        log.info("User {} subscribed to push notifications", user.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Unsubscribe from push notifications.
     */
    @PostMapping("/unsubscribe")
    @Operation(summary = "Unsubscribe", description = "Unsubscribe from push notifications")
    public ResponseEntity<Map<String, Object>> unsubscribe(@RequestBody Map<String, String> request) {
        String endpoint = request.get("endpoint");
        if (endpoint == null || endpoint.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Endpoint is required"));
        }

        webPushService.unsubscribe(endpoint);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Successfully unsubscribed from push notifications");

        return ResponseEntity.ok(response);
    }

    /**
     * Check subscription status.
     */
    @GetMapping("/status")
    @Operation(summary = "Check subscription status", description = "Check if push notifications are enabled and subscription exists")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam(required = false) String endpoint) {
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", webPushService.isEnabled());
        response.put("totalSubscriptions", subscriptionRepository.countByIsActiveTrue());

        if (endpoint != null && !endpoint.isEmpty()) {
            boolean subscribed = subscriptionRepository.existsByEndpoint(endpoint);
            response.put("subscribed", subscribed);
        }

        return ResponseEntity.ok(response);
    }

    // ==================== Admin endpoints ====================

    /**
     * Send push notification to specific customer (admin only).
     */
    @PostMapping("/admin/send/customer/{customerId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send to customer", description = "Send push notification to a specific customer")
    public ResponseEntity<Map<String, Object>> sendToCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody PushNotificationRequest request) {

        if (request.getType() == null) {
            request.setType(PushNotificationType.SYSTEM);
        }

        webPushService.sendToCustomer(customerId, request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Push notification queued for delivery"
        ));
    }

    /**
     * Send push notification to all subscribers (admin only).
     */
    @PostMapping("/admin/send/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Broadcast notification", description = "Send push notification to all active subscribers")
    public ResponseEntity<Map<String, Object>> sendBroadcast(
            @Valid @RequestBody PushNotificationRequest request) {

        if (request.getType() == null) {
            request.setType(PushNotificationType.PROMOTION);
        }

        long subscriberCount = subscriptionRepository.countByIsActiveTrue();
        webPushService.sendToAll(request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Push notification queued for " + subscriberCount + " subscribers"
        ));
    }

    /**
     * Get all subscriptions (admin only).
     */
    @GetMapping("/admin/subscriptions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List subscriptions", description = "Get all active push subscriptions")
    public ResponseEntity<List<PushSubscription>> getSubscriptions() {
        List<PushSubscription> subscriptions = subscriptionRepository.findByIsActiveTrue();
        return ResponseEntity.ok(subscriptions);
    }

    /**
     * Get push notification statistics (admin only).
     */
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get statistics", description = "Get push notification statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalActiveSubscriptions", subscriptionRepository.countByIsActiveTrue());
        stats.put("pushEnabled", webPushService.isEnabled());
        return ResponseEntity.ok(stats);
    }
}
