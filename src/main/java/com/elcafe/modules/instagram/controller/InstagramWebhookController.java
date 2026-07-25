package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.service.InstagramBotService;
import com.elcafe.modules.instagram.service.InstagramWebhookService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Receives and validates Meta webhook events for the Instagram integration.
 *
 *  GET  /api/v1/instagram/webhook  – Meta hub challenge verification
 *  POST /api/v1/instagram/webhook  – Incoming events (DMs, comments)
 *
 * <p>This endpoint is PUBLIC (permitAll in SecurityConfig) — Meta calls it with no credentials of
 * ours. Its only authentication is the per-tenant material in the request itself: the verify token on
 * the GET handshake, and the HMAC-SHA256 signature on every POST. Both fail closed.
 *
 * <p>Tenancy: the POST body's {@code entry.id} is the Instagram business account that received the
 * event, which maps to exactly one restaurant's config. That config's app secret verifies the
 * signature, and its restaurant owns everything the delivery creates.
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
     * We resolve the restaurant from the verify token and echo the challenge back.
     *
     * <p>Responds as text/plain: the challenge is reflected caller-supplied content, and letting the
     * client negotiate text/html would make this a reflected-XSS vector on the API origin. All
     * failures return one opaque 403 so the endpoint does not reveal whether an integration exists.
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode")         String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge")    String challenge) {

        if (!"subscribe".equals(mode)) {
            log.warn("Instagram webhook verification: unexpected hub.mode={}", mode);
            return ResponseEntity.status(403).build();
        }

        InstagramBotConfig config = botService.getConfigByVerifyToken(verifyToken);
        if (config == null) {
            log.warn("Instagram webhook verification failed: no config matches the presented verify token");
            return ResponseEntity.status(403).build();
        }

        log.info("Instagram webhook verified for restaurant {}", config.getRestaurantId());
        return ResponseEntity.ok(challenge);
    }

    /**
     * Meta delivers events via POST. Processing is delegated asynchronously so we can return 200
     * immediately (Meta re-delivers if no 200 within ~20 s).
     *
     * <p>The body is taken as {@code byte[]}, not {@code String}: Meta signs the exact byte stream,
     * and letting Spring decode it to a String first can change those bytes when the request omits a
     * charset — producing a digest mismatch on legitimate traffic.
     */
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawBody) {

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(
                    new String(rawBody, StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Instagram webhook: unparseable payload ({})", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        // The tenant must be identified BEFORE the signature can be checked, because the app secret
        // is per-restaurant. Parsing untrusted JSON first is inherent to a multi-tenant receiver;
        // nothing is read from the payload beyond the account id until the signature verifies.
        String accountId = webhookService.resolveAccountId(payload);
        InstagramBotConfig config = botService.getConfigByInstagramAccountId(accountId);
        if (config == null) {
            log.warn("Instagram webhook rejected: no configuration for account {}", accountId);
            return ResponseEntity.status(403).build();
        }

        if (!webhookService.verifySignature(rawBody, signature, config)) {
            log.warn("Instagram webhook rejected: signature verification failed for account {}", accountId);
            return ResponseEntity.status(403).build();
        }

        webhookService.processWebhookPayload(payload);
        return ResponseEntity.ok().build();
    }
}
