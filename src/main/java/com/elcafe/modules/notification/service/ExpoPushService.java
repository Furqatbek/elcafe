package com.elcafe.modules.notification.service;

import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.entity.WaiterDevice;
import com.elcafe.modules.notification.repository.WaiterDeviceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends push notifications to waiter devices via Expo's push API
 * (https://exp.host/--/api/v2/push/send). No Firebase credentials required.
 *
 * Best-effort: failures are logged, never thrown to the caller — a push that
 * doesn't send must not break the business action that triggered it. Tokens
 * Expo reports as DeviceNotRegistered are pruned from the store.
 */
@Slf4j
@Service
public class ExpoPushService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final RestTemplate restTemplate;
    private final WaiterDeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;

    public ExpoPushService(RestTemplateBuilder builder,
                           WaiterDeviceRepository deviceRepository,
                           ObjectMapper objectMapper) {
        this.restTemplate = builder.build();
        this.deviceRepository = deviceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Push a persisted waiter notification to all of the waiter's registered
     * devices. {@code notification.id} is sent as data.notificationId (and
     * data.id) so the app can mark it read on tap.
     */
    public void sendToWaiter(Long waiterId, Notification notification, Map<String, Object> extraData) {
        List<WaiterDevice> devices = deviceRepository.findByWaiterId(waiterId);
        if (devices.isEmpty()) {
            log.debug("No registered devices for waiter {}, skipping push", waiterId);
            return;
        }

        String type = notification.getType() != null ? notification.getType().name() : "SYSTEM_ALERT";
        String channelId = channelForType(type);

        Map<String, Object> data = new HashMap<>();
        if (extraData != null) {
            data.putAll(extraData);
        }
        data.put("notificationId", notification.getId());
        data.put("id", notification.getId());
        data.put("type", type);

        List<Map<String, Object>> messages = new ArrayList<>(devices.size());
        for (WaiterDevice device : devices) {
            Map<String, Object> message = new HashMap<>();
            message.put("to", device.getToken());
            message.put("title", notification.getTitle());
            message.put("body", notification.getMessage());
            message.put("sound", "default");
            message.put("channelId", channelId);
            message.put("data", data);
            messages.add(message);
        }

        sendAndPrune(messages, devices);
    }

    private void sendAndPrune(List<Map<String, Object>> messages, List<WaiterDevice> devices) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            JsonNode response = restTemplate.postForObject(
                    EXPO_PUSH_URL, new HttpEntity<>(messages, headers), JsonNode.class);

            // Expo returns { "data": [ { status, details:{ error } }, ... ] },
            // index-aligned with the messages we sent. Prune dead tokens.
            JsonNode receipts = response != null ? response.get("data") : null;
            if (receipts == null || !receipts.isArray()) {
                return;
            }
            for (int i = 0; i < receipts.size() && i < devices.size(); i++) {
                JsonNode receipt = receipts.get(i);
                if ("error".equals(receipt.path("status").asText())
                        && "DeviceNotRegistered".equals(receipt.path("details").path("error").asText())) {
                    String deadToken = devices.get(i).getToken();
                    log.info("Pruning DeviceNotRegistered token for waiter {}", devices.get(i).getWaiterId());
                    deviceRepository.findByToken(deadToken).ifPresent(deviceRepository::delete);
                }
            }
        } catch (Exception e) {
            // best-effort: never propagate a push failure to the business flow
            log.warn("Expo push send failed: {}", e.getMessage());
        }
    }

    /** Android channel per notification type (the app registers default/orders/kitchen). */
    private String channelForType(String type) {
        return switch (type) {
            case "KITCHEN_ALERT" -> "kitchen";
            case "ORDER_STATUS", "NEW_ORDER", "TABLE_READY" -> "orders";
            default -> "default";
        };
    }
}
