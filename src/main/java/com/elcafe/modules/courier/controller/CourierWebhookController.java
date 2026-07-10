package com.elcafe.modules.courier.controller;

import com.elcafe.modules.courier.service.LocalCourierAdapter;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Receives delivery-status callbacks from the courier integration. This endpoint is {@code permitAll} at
 * the security-filter layer (a courier can't present a user JWT), so it authenticates itself with a shared
 * secret: the caller must send {@code X-Webhook-Secret} matching {@code app.courier.webhook-secret}.
 *
 * <p>It fails <b>closed</b>: if the secret is unset/blank, or the header is missing or wrong, the callback
 * is rejected. Previously this endpoint accepted an unauthenticated, unsigned payload and mutated order
 * state — anyone could POST arbitrary delivery-status changes.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/courier/webhook")
@RequiredArgsConstructor
@Tag(name = "Courier", description = "Courier integration and webhook endpoints")
public class CourierWebhookController {

    private final LocalCourierAdapter courierAdapter;

    @Value("${app.courier.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/delivery-status")
    @Operation(summary = "Delivery status webhook", description = "Receive delivery status updates from courier")
    public ResponseEntity<ApiResponse<Void>> deliveryStatusWebhook(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String providedSecret,
            @RequestBody Map<String, Object> payload) {
        if (!isAuthorized(providedSecret)) {
            log.warn("Rejected courier webhook: missing/invalid X-Webhook-Secret");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }

        String trackingId = (String) payload.get("trackingId");
        String status = (String) payload.get("status");
        log.info("Courier webhook: trackingId={}, status={}", trackingId, status);

        if (trackingId != null && status != null) {
            courierAdapter.updateDeliveryStatus(trackingId, status);
        }

        return ResponseEntity.ok(ApiResponse.success("Webhook processed", null));
    }

    /** Fail closed: reject when no secret is configured, or the header is absent / doesn't match. */
    private boolean isAuthorized(String providedSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("app.courier.webhook-secret is not configured — refusing all courier webhooks");
            return false;
        }
        if (providedSecret == null) {
            return false;
        }
        // Constant-time comparison to avoid leaking the secret via response timing.
        return MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                providedSecret.getBytes(StandardCharsets.UTF_8));
    }
}
