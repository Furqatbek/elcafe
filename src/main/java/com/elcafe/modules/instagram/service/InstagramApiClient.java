package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Low-level HTTP client for Meta Graph API calls required by Instagram integration:
 * - Send direct messages (text, quick-reply buttons)
 * - Reply to comments on business posts
 *
 * All operations require a valid Page Access Token stored in {@link InstagramBotConfig}.
 */
@Slf4j
@Service
public class InstagramApiClient {

    private static final String GRAPH_BASE = "https://graph.facebook.com/v19.0";

    private final RestTemplate restTemplate;

    public InstagramApiClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    // -------------------------------------------------------------------------
    // Direct Messages
    // -------------------------------------------------------------------------

    /**
     * Send a plain-text DM to an Instagram user.
     *
     * @param config    active bot config (provides access token + account id)
     * @param recipientIgsid  Instagram Scoped User ID of the recipient
     * @param text      message text
     * @return true on success
     */
    @CircuitBreaker(name = "instagram", fallbackMethod = "sendMessageCircuitOpen")
    public boolean sendMessage(InstagramBotConfig config, String recipientIgsid, String text) {
        Map<String, Object> body = Map.of(
                "recipient", Map.of("id", recipientIgsid),
                "message",   Map.of("text", text)
        );
        return postToMessagesApi(config, body);
    }

    /**
     * Send a text DM with quick-reply buttons.
     *
     * @param quickReplies list of {title, payload} maps (max 13, title max 20 chars)
     */
    @CircuitBreaker(name = "instagram", fallbackMethod = "sendQuickRepliesCircuitOpen")
    public boolean sendMessageWithQuickReplies(InstagramBotConfig config, String recipientIgsid,
                                               String text, List<Map<String, String>> quickReplies) {
        List<Map<String, Object>> qr = quickReplies.stream()
                .map(r -> Map.<String, Object>of(
                        "content_type", "text",
                        "title",        r.getOrDefault("title", ""),
                        "payload",      r.getOrDefault("payload", "")))
                .toList();

        Map<String, Object> body = Map.of(
                "recipient", Map.of("id", recipientIgsid),
                "message",   Map.of("text", text, "quick_replies", qr)
        );
        return postToMessagesApi(config, body);
    }

    // -------------------------------------------------------------------------
    // Comment replies
    // -------------------------------------------------------------------------

    /**
     * Post a public reply to an Instagram comment.
     *
     * @param config      active bot config
     * @param commentId   numeric comment ID from the webhook event
     * @param replyText   reply message
     * @return true on success
     */
    @CircuitBreaker(name = "instagram", fallbackMethod = "replyToCommentCircuitOpen")
    public boolean replyToComment(InstagramBotConfig config, String commentId, String replyText) {
        String url = GRAPH_BASE + "/" + commentId + "/replies?access_token=" + config.getAccessToken();
        Map<String, Object> body = Map.of("message", replyText);
        try {
            HttpHeaders headers = jsonHeaders();
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            boolean ok = response.getStatusCode().is2xxSuccessful();
            if (!ok) log.warn("Comment reply failed: status={}", response.getStatusCode());
            return ok;
        } catch (RestClientException e) {
            log.error("Failed to reply to comment {}: {}", commentId, e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private boolean postToMessagesApi(InstagramBotConfig config, Map<String, Object> body) {
        String url = GRAPH_BASE + "/" + config.getInstagramAccountId()
                + "/messages?access_token=" + config.getAccessToken();
        try {
            HttpHeaders headers = jsonHeaders();
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            boolean ok = response.getStatusCode().is2xxSuccessful();
            if (!ok) log.warn("Instagram DM send failed: status={}", response.getStatusCode());
            return ok;
        } catch (RestClientException e) {
            log.error("Failed to send Instagram DM: {}", e.getMessage());
            return false;
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // Open-circuit fallbacks (fire only on CallNotPermittedException): match the boolean
    // failure contract — callers already treat false as "message not delivered".
    @SuppressWarnings("unused")
    private boolean sendMessageCircuitOpen(InstagramBotConfig config, String recipientIgsid,
                                           String text, CallNotPermittedException e) {
        log.warn("Instagram circuit open, dropping DM to {}", recipientIgsid);
        return false;
    }

    @SuppressWarnings("unused")
    private boolean sendQuickRepliesCircuitOpen(InstagramBotConfig config, String recipientIgsid,
                                                String text, java.util.List<java.util.Map<String, String>> quickReplies,
                                                CallNotPermittedException e) {
        log.warn("Instagram circuit open, dropping quick-reply DM to {}", recipientIgsid);
        return false;
    }

    @SuppressWarnings("unused")
    private boolean replyToCommentCircuitOpen(InstagramBotConfig config, String commentId,
                                              String replyText, CallNotPermittedException e) {
        log.warn("Instagram circuit open, dropping comment reply to {}", commentId);
        return false;
    }
}
