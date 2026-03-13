package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Processes incoming Meta webhook payloads for the Instagram integration.
 *
 * Supported event types:
 *  - messages       → DM text or quick-reply from a user
 *  - messaging_postbacks → quick-reply postbacks
 *  - comments       → comment on a business post (triggers auto-reply if enabled)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramWebhookService {

    private final InstagramBotService botService;
    private final InstagramApiClient  apiClient;

    // -------------------------------------------------------------------------
    // Signature verification
    // -------------------------------------------------------------------------

    /**
     * Verify the X-Hub-Signature-256 header sent by Meta.
     * Returns true when no app secret is configured (dev / missing-config fallback).
     */
    public boolean verifySignature(String rawBody, String signatureHeader) {
        InstagramBotConfig config = botService.getActiveConfig();
        if (config == null || config.getAppSecret() == null || config.getAppSecret().isBlank()) {
            log.debug("No app secret configured – skipping signature check");
            return true;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("Missing or malformed X-Hub-Signature-256 header");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(digest);
            return expected.equalsIgnoreCase(signatureHeader);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Webhook event dispatch
    // -------------------------------------------------------------------------

    /**
     * Entry point called from the controller (runs on a background thread via @Async).
     *
     * @param payload deserialized JSON body as nested Map
     */
    @Async
    public void processWebhookPayload(Map<String, Object> payload) {
        try {
            String object = (String) payload.get("object");
            if (!"instagram".equalsIgnoreCase(object)) {
                log.debug("Ignoring non-instagram webhook object: {}", object);
                return;
            }

            List<Map<String, Object>> entries = castList(payload.get("entry"));
            if (entries == null) return;

            for (Map<String, Object> entry : entries) {
                processEntry(entry);
            }
        } catch (Exception e) {
            log.error("Error processing Instagram webhook payload: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void processEntry(Map<String, Object> entry) {
        // DM messages are under entry.messaging[]
        List<Map<String, Object>> messaging = castList(entry.get("messaging"));
        if (messaging != null) {
            for (Map<String, Object> event : messaging) {
                processMessagingEvent(event);
            }
        }

        // Comment events are under entry.changes[]
        List<Map<String, Object>> changes = castList(entry.get("changes"));
        if (changes != null) {
            for (Map<String, Object> change : changes) {
                processChangeEvent(change);
            }
        }
    }

    private void processMessagingEvent(Map<String, Object> event) {
        try {
            Map<String, Object> sender = castMap(event.get("sender"));
            if (sender == null) return;
            String senderIgsid = (String) sender.get("id");
            if (senderIgsid == null) return;

            // Try to get username from sender info (usually not present in basic webhook)
            String username = (String) sender.getOrDefault("username", null);

            String text             = null;
            String quickReplyPayload = null;

            Map<String, Object> message = castMap(event.get("message"));
            if (message != null) {
                text = (String) message.get("text");

                // Check for quick-reply payload
                Map<String, Object> quickReply = castMap(message.get("quick_reply"));
                if (quickReply != null) {
                    quickReplyPayload = (String) quickReply.get("payload");
                }
            }

            // Postback events (e.g. persistent menu taps)
            Map<String, Object> postback = castMap(event.get("postback"));
            if (postback != null) {
                quickReplyPayload = (String) postback.get("payload");
            }

            if (text != null || quickReplyPayload != null) {
                botService.handleIncomingMessage(senderIgsid, username, text, quickReplyPayload);
            }
        } catch (Exception e) {
            log.error("Error processing Instagram messaging event: {}", e.getMessage(), e);
        }
    }

    private void processChangeEvent(Map<String, Object> change) {
        try {
            String field = (String) change.get("field");
            if (!"comments".equals(field)) return;

            Map<String, Object> value = castMap(change.get("value"));
            if (value == null) return;

            InstagramBotConfig config = botService.getActiveConfig();
            if (config == null) return;
            if (!Boolean.TRUE.equals(config.getAutoReplyEnabled())) return;

            String commentId   = (String) value.get("id");
            String commentText = (String) value.get("text");

            if (commentId == null) return;

            // Check it's not an echo of our own reply
            Boolean fromMe = (Boolean) value.get("from_me");
            if (Boolean.TRUE.equals(fromMe)) return;

            String replyTemplate = config.getAutoReplyTemplate();
            if (replyTemplate == null || replyTemplate.isBlank()) {
                log.debug("Auto-reply enabled but no template set, skipping comment {}", commentId);
                return;
            }

            // Basic placeholder replacement
            String reply = replyTemplate
                    .replace("{comment}", commentText != null ? commentText : "")
                    .replace("{comment_text}", commentText != null ? commentText : "");

            log.info("Auto-replying to comment {}", commentId);
            apiClient.replyToComment(config, commentId, reply);
        } catch (Exception e) {
            log.error("Error processing Instagram change event: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        if (o instanceof List<?>) return (List<Map<String, Object>>) o;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map<?, ?>) return (Map<String, Object>) o;
        return null;
    }
}
