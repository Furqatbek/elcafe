package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.service.InstagramBotService;
import com.elcafe.modules.instagram.service.InstagramWebhookService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives and validates Meta webhook events for the Instagram integration.
 *
 *  GET  /api/v1/instagram/webhook  – Meta hub challenge verification
 *  POST /api/v1/instagram/webhook  – Incoming events (DMs, comments)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/instagram/webhook")
@RequiredArgsConstructor
public class InstagramWebhookController {

    private final InstagramWebhookService webhookService;
    private final InstagramBotService     botService;
    private final ObjectMapper            objectMapper;

    /**
     * Meta sends a GET with hub.mode=subscribe, hub.verify_token, and hub.challenge.
     * We verify the token matches and echo back the challenge.
     */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode")         String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge")    String challenge) {

        if (!"subscribe".equals(mode)) {
            log.warn("Instagram webhook verification: unexpected hub.mode={}", mode);
            return ResponseEntity.badRequest().body("Invalid mode");
        }

        var config = botService.getActiveConfig();
        if (config == null) {
            log.warn("Instagram webhook verification: no active config found");
            return ResponseEntity.status(403).body("No active config");
        }

        String expectedToken = config.getVerifyToken();
        if (expectedToken == null || !expectedToken.equals(verifyToken)) {
            log.warn("Instagram webhook verification: token mismatch");
            return ResponseEntity.status(403).body("Forbidden");
        }

        log.info("Instagram webhook verified successfully");
        return ResponseEntity.ok(challenge);
    }

    /**
     * Meta delivers events via POST. Processing is delegated asynchronously so
     * we can return 200 immediately (Meta re-delivers if no 200 within ~20 s).
     */
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String rawBody) {

        if (!webhookService.verifySignature(rawBody, signature)) {
            log.warn("Instagram webhook: signature verification failed");
            return ResponseEntity.status(403).build();
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    rawBody, new TypeReference<Map<String, Object>>() {});
            webhookService.processWebhookPayload(payload);
        } catch (Exception e) {
            log.error("Failed to parse Instagram webhook payload: {}", e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}
