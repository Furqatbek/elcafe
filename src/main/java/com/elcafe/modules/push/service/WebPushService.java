package com.elcafe.modules.push.service;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.push.dto.PushNotificationRequest;
import com.elcafe.modules.push.dto.PushSubscriptionRequest;
import com.elcafe.modules.push.entity.PushNotificationLog;
import com.elcafe.modules.push.entity.PushSubscription;
import com.elcafe.modules.push.enums.PushNotificationStatus;
import com.elcafe.modules.push.enums.PushNotificationType;
import com.elcafe.modules.push.repository.PushNotificationLogRepository;
import com.elcafe.modules.push.repository.PushSubscriptionRepository;
import com.elcafe.modules.auth.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for sending Web Push notifications using VAPID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final PushNotificationLogRepository logRepository;
    private final ObjectMapper objectMapper;

    @Value("${push.vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${push.vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${push.vapid.subject:mailto:admin@elcafe.com}")
    private String vapidSubject;

    private PushService pushService;

    @PostConstruct
    public void init() {
        // Register BouncyCastle provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // Initialize push service if keys are configured
        if (vapidPublicKey != null && !vapidPublicKey.isEmpty() &&
            vapidPrivateKey != null && !vapidPrivateKey.isEmpty()) {
            try {
                pushService = new PushService();
                pushService.setPublicKey(vapidPublicKey);
                pushService.setPrivateKey(vapidPrivateKey);
                pushService.setSubject(vapidSubject);
                log.info("Web Push service initialized successfully");
            } catch (GeneralSecurityException | RuntimeException e) {
                // Malformed keys throw IllegalArgumentException ("Invalid point encoding") from the
                // webpush lib, not GeneralSecurityException. Either way, bad VAPID config should disable
                // push (isEnabled() == false) and leave the app running — never abort startup.
                pushService = null;
                log.error("Failed to initialize Web Push service; push notifications disabled: {}", e.getMessage());
            }
        } else {
            log.warn("VAPID keys not configured. Web Push notifications will be disabled.");
        }
    }

    /**
     * Get the VAPID public key for client subscription.
     */
    public String getVapidPublicKey() {
        return vapidPublicKey;
    }

    /**
     * Check if push service is configured and ready.
     */
    public boolean isEnabled() {
        return pushService != null;
    }

    /**
     * Subscribe a customer to push notifications.
     */
    @Transactional
    public PushSubscription subscribeCustomer(Customer customer, PushSubscriptionRequest request) {
        // Check if subscription already exists
        Optional<PushSubscription> existing = subscriptionRepository.findByEndpoint(request.getEndpoint());
        if (existing.isPresent()) {
            PushSubscription subscription = existing.get();
            // Update existing subscription
            subscription.setCustomer(customer);
            subscription.setP256dhKey(request.getP256dh());
            subscription.setAuthKey(request.getAuth());
            subscription.setDeviceType(request.getDeviceType());
            subscription.setBrowser(request.getBrowser());
            subscription.setUserAgent(request.getUserAgent());
            subscription.setIsActive(true);
            subscription.markAsUsed();
            return subscriptionRepository.save(subscription);
        }

        // Create new subscription
        PushSubscription subscription = PushSubscription.builder()
                .customer(customer)
                .endpoint(request.getEndpoint())
                .p256dhKey(request.getP256dh())
                .authKey(request.getAuth())
                .deviceType(request.getDeviceType())
                .browser(request.getBrowser())
                .userAgent(request.getUserAgent())
                .build();

        return subscriptionRepository.save(subscription);
    }

    /**
     * Subscribe an admin user to push notifications.
     */
    @Transactional
    public PushSubscription subscribeUser(User user, PushSubscriptionRequest request) {
        Optional<PushSubscription> existing = subscriptionRepository.findByEndpoint(request.getEndpoint());
        if (existing.isPresent()) {
            PushSubscription subscription = existing.get();
            subscription.setUser(user);
            subscription.setP256dhKey(request.getP256dh());
            subscription.setAuthKey(request.getAuth());
            subscription.setDeviceType(request.getDeviceType());
            subscription.setBrowser(request.getBrowser());
            subscription.setUserAgent(request.getUserAgent());
            subscription.setIsActive(true);
            subscription.markAsUsed();
            return subscriptionRepository.save(subscription);
        }

        PushSubscription subscription = PushSubscription.builder()
                .user(user)
                .endpoint(request.getEndpoint())
                .p256dhKey(request.getP256dh())
                .authKey(request.getAuth())
                .deviceType(request.getDeviceType())
                .browser(request.getBrowser())
                .userAgent(request.getUserAgent())
                .build();

        return subscriptionRepository.save(subscription);
    }

    /**
     * Unsubscribe from push notifications by endpoint.
     */
    @Transactional
    public void unsubscribe(String endpoint) {
        subscriptionRepository.deactivateByEndpoint(endpoint);
        log.debug("Deactivated push subscription for endpoint: {}", endpoint);
    }

    /**
     * Send push notification to a specific customer.
     */
    @Async
    @Transactional
    public void sendToCustomer(Long customerId, PushNotificationRequest request) {
        if (!isEnabled()) {
            log.warn("Push service not enabled, skipping notification to customer {}", customerId);
            return;
        }

        List<PushSubscription> subscriptions = subscriptionRepository.findByCustomerIdAndIsActiveTrue(customerId);
        for (PushSubscription subscription : subscriptions) {
            sendNotification(subscription, request);
        }
    }

    /**
     * Send push notification to a specific user (admin).
     */
    @Async
    @Transactional
    public void sendToUser(Long userId, PushNotificationRequest request) {
        if (!isEnabled()) {
            log.warn("Push service not enabled, skipping notification to user {}", userId);
            return;
        }

        List<PushSubscription> subscriptions = subscriptionRepository.findByUserIdAndIsActiveTrue(userId);
        for (PushSubscription subscription : subscriptions) {
            sendNotification(subscription, request);
        }
    }

    /**
     * Send push notification to all active subscriptions.
     */
    @Async
    @Transactional
    public void sendToAll(PushNotificationRequest request) {
        if (!isEnabled()) {
            log.warn("Push service not enabled, skipping broadcast notification");
            return;
        }

        List<PushSubscription> subscriptions = subscriptionRepository.findByIsActiveTrue();
        log.info("Sending push notification to {} subscriptions", subscriptions.size());

        for (PushSubscription subscription : subscriptions) {
            sendNotification(subscription, request);
        }
    }

    /**
     * Send notification to a specific subscription.
     */
    private void sendNotification(PushSubscription subscription, PushNotificationRequest request) {
        // Create log entry
        PushNotificationLog logEntry = PushNotificationLog.builder()
                .subscription(subscription)
                .customer(subscription.getCustomer())
                .title(request.getTitle())
                .body(request.getBody())
                .icon(request.getIcon())
                .image(request.getImage())
                .badge(request.getBadge())
                .tag(request.getTag())
                .data(request.getData())
                .notificationType(request.getType() != null ? request.getType() : PushNotificationType.SYSTEM)
                .status(PushNotificationStatus.PENDING)
                .build();

        try {
            // Build payload
            Map<String, Object> payload = buildPayload(request);
            String payloadJson = objectMapper.writeValueAsString(payload);

            // Create subscription object for web-push library
            Subscription webPushSubscription = new Subscription(
                    subscription.getEndpoint(),
                    new Subscription.Keys(subscription.getP256dhKey(), subscription.getAuthKey())
            );

            // Create and send notification
            Notification notification = new Notification(webPushSubscription, payloadJson);
            pushService.send(notification);

            // Mark as sent
            logEntry.markAsSent();
            subscription.markAsUsed();
            subscriptionRepository.save(subscription);

            log.debug("Push notification sent to subscription {}", subscription.getId());

        } catch (Exception e) {
            log.error("Failed to send push notification to subscription {}: {}",
                    subscription.getId(), e.getMessage());
            logEntry.markAsFailed(e.getMessage());

            // Deactivate subscription if endpoint is gone
            if (isGoneError(e)) {
                subscription.deactivate();
                subscriptionRepository.save(subscription);
                log.info("Deactivated gone subscription {}", subscription.getId());
            }
        }

        logRepository.save(logEntry);
    }

    /**
     * Build notification payload.
     */
    private Map<String, Object> buildPayload(PushNotificationRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", request.getTitle());

        if (request.getBody() != null) {
            payload.put("body", request.getBody());
        }
        if (request.getIcon() != null) {
            payload.put("icon", request.getIcon());
        }
        if (request.getImage() != null) {
            payload.put("image", request.getImage());
        }
        if (request.getBadge() != null) {
            payload.put("badge", request.getBadge());
        }
        if (request.getTag() != null) {
            payload.put("tag", request.getTag());
        }
        if (request.getData() != null) {
            payload.put("data", request.getData());
        }
        if (request.getActions() != null && !request.getActions().isEmpty()) {
            payload.put("actions", request.getActions());
        }

        return payload;
    }

    /**
     * Check if the error indicates the subscription is gone (410 status).
     */
    private boolean isGoneError(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("410") || message.contains("Gone"));
    }

    /**
     * Get subscription count.
     */
    public long getActiveSubscriptionCount() {
        return subscriptionRepository.countByIsActiveTrue();
    }

    /**
     * Get customer subscription count.
     */
    public long getCustomerSubscriptionCount(Long customerId) {
        return subscriptionRepository.countByCustomerIdAndIsActiveTrue(customerId);
    }
}
