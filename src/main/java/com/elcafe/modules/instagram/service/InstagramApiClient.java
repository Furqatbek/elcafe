package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Low-level HTTP client for Meta Graph API calls required by Instagram integration:
 * - Send direct messages (text, quick-reply buttons)
 * - Reply to comments on business posts
 *
 * All operations require a valid Page Access Token stored in {@link InstagramBotConfig}.
 *
 * <p><b>Failure contract.</b> The public methods return {@code boolean} — callers treat {@code false}
 * as "not delivered" — but the HTTP call is deliberately allowed to THROW out of the annotated
 * method, and only the fallback converts that into {@code false}. resilience4j records a failure
 * only when the decorated method throws, so catching inside it (as this class used to) left the
 * breaker seeing a 100% success rate no matter how broken Meta was. Note the conversion cannot be
 * done by wrapping a private method either: an internal call would bypass the Spring AOP proxy and
 * the annotation with it.
 */
@Slf4j
@Service
public class InstagramApiClient {

    /**
     * Meta retires Graph versions on a roughly two-year clock, so the base URL and version are
     * configuration rather than a constant — an upgrade should not need a code change, and a test
     * needs to be able to point this at a stub.
     */
    private final String graphBase;

    /** Graph object ids are numeric, occasionally with an underscore separator. Nothing else. */
    private static final Pattern GRAPH_ID = Pattern.compile("^[0-9_]{1,40}$");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InstagramApiClient(RestTemplateBuilder builder,
                              @Value("${instagram.graph.base-url:https://graph.facebook.com}") String baseUrl,
                              @Value("${instagram.graph.version:v19.0}") String version) {
        this.graphBase = baseUrl.replaceAll("/+$", "") + "/" + version;
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
    @CircuitBreaker(name = "instagram", fallbackMethod = "sendMessageFallback")
    public InstagramSendResult sendMessage(InstagramBotConfig config, String recipientIgsid, String text) {
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
    @CircuitBreaker(name = "instagram", fallbackMethod = "sendQuickRepliesFallback")
    public InstagramSendResult sendMessageWithQuickReplies(InstagramBotConfig config, String recipientIgsid,
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
    @CircuitBreaker(name = "instagram", fallbackMethod = "replyToCommentFallback")
    public InstagramSendResult replyToComment(InstagramBotConfig config, String commentId, String replyText) {
        // commentId arrives straight off the webhook. It used to be concatenated into a string that
        // RestTemplate treats as a URI template, so a value like "me/subscribed_apps?access_token="
        // re-targeted the POST at a different Graph edge while still appending the merchant's token.
        if (commentId == null || !GRAPH_ID.matcher(commentId).matches()) {
            log.warn("Refusing Instagram comment reply: malformed comment id");
            return InstagramSendResult.failed(
                    InstagramSendResult.Failure.INVALID_REQUEST, 0, "malformed comment id");
        }
        String url = UriComponentsBuilder.fromHttpUrl(graphBase)
                .pathSegment(commentId, "replies")
                .build(true)
                .toUriString();
        postJson(config, url, Map.of("message", replyText));
        return InstagramSendResult.ok();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private InstagramSendResult postToMessagesApi(InstagramBotConfig config, Map<String, Object> body) {
        String accountId = config.getInstagramAccountId();
        if (accountId == null || !GRAPH_ID.matcher(accountId).matches()) {
            log.warn("Refusing Instagram send: malformed instagram account id");
            return InstagramSendResult.failed(
                    InstagramSendResult.Failure.INVALID_REQUEST, 0, "malformed instagram account id");
        }
        String url = UriComponentsBuilder.fromHttpUrl(graphBase)
                .pathSegment(accountId, "messages")
                .build(true)
                .toUriString();
        postJson(config, url, body);
        return InstagramSendResult.ok();
    }

    /**
     * Issue the POST. Any transport error or non-2xx status leaves this method as an exception —
     * that is what the circuit breaker counts. {@code DefaultResponseErrorHandler} already throws on
     * every non-2xx, so there is no "unsuccessful response" branch to inspect.
     */
    private void postJson(InstagramBotConfig config, String url, Map<String, Object> body) {
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, authHeaders(config)), Map.class);
    }

    /**
     * The page access token travels as a bearer header, not a query parameter: query strings are
     * recorded verbatim by proxies, access logs and APM span tags, so a token there is one logging
     * interceptor away from being leaked.
     */
    private HttpHeaders authHeaders(InstagramBotConfig config) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (config.getAccessToken() != null) {
            h.setBearerAuth(config.getAccessToken());
        }
        return h;
    }

    // -------------------------------------------------------------------------
    // Fallbacks
    //
    // Two overloads per method. resilience4j picks the most specific match, so an open circuit is
    // reported distinctly from a live failure — but BOTH restore the boolean contract callers rely
    // on, which is what lets the annotated methods above throw in the first place.
    // -------------------------------------------------------------------------

    @SuppressWarnings("unused")
    private InstagramSendResult sendMessageFallback(InstagramBotConfig config, String recipientIgsid,
                                                    String text, CallNotPermittedException e) {
        log.warn("Instagram circuit open, dropping DM to {}", recipientIgsid);
        return InstagramSendResult.failed(InstagramSendResult.Failure.CIRCUIT_OPEN, 0, "circuit open");
    }

    @SuppressWarnings("unused")
    private InstagramSendResult sendMessageFallback(InstagramBotConfig config, String recipientIgsid,
                                                    String text, Throwable t) {
        return describe(t, "DM to " + recipientIgsid);
    }

    @SuppressWarnings("unused")
    private InstagramSendResult sendQuickRepliesFallback(InstagramBotConfig config, String recipientIgsid,
                                                         String text, List<Map<String, String>> quickReplies,
                                                         CallNotPermittedException e) {
        log.warn("Instagram circuit open, dropping quick-reply DM to {}", recipientIgsid);
        return InstagramSendResult.failed(InstagramSendResult.Failure.CIRCUIT_OPEN, 0, "circuit open");
    }

    @SuppressWarnings("unused")
    private InstagramSendResult sendQuickRepliesFallback(InstagramBotConfig config, String recipientIgsid,
                                                         String text, List<Map<String, String>> quickReplies,
                                                         Throwable t) {
        return describe(t, "quick-reply DM to " + recipientIgsid);
    }

    @SuppressWarnings("unused")
    private InstagramSendResult replyToCommentFallback(InstagramBotConfig config, String commentId,
                                                       String replyText, CallNotPermittedException e) {
        log.warn("Instagram circuit open, dropping comment reply to {}", commentId);
        return InstagramSendResult.failed(InstagramSendResult.Failure.CIRCUIT_OPEN, 0, "circuit open");
    }

    @SuppressWarnings("unused")
    private InstagramSendResult replyToCommentFallback(InstagramBotConfig config, String commentId,
                                                       String replyText, Throwable t) {
        return describe(t, "comment reply to " + commentId);
    }

    // -------------------------------------------------------------------------
    // Error interpretation
    // -------------------------------------------------------------------------

    /**
     * Turn whatever went wrong into a typed result. Meta returns a JSON envelope on error:
     * <pre>{"error":{"message":"...","type":"OAuthException","code":190,"error_subcode":460}}</pre>
     * That body used to be deserialized into a {@code Map} that nothing ever read, so every distinct
     * cause arrived at the caller as an identical {@code false}.
     */
    private InstagramSendResult describe(Throwable t, String what) {
        if (t instanceof HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            int code = 0;
            int subCode = 0;
            String message = null;
            try {
                JsonNode error = objectMapper.readTree(http.getResponseBodyAsString()).path("error");
                if (!error.isMissingNode()) {
                    code = error.path("code").asInt(0);
                    subCode = error.path("error_subcode").asInt(0);
                    message = error.path("message").asText(null);
                }
            } catch (Exception parseFailure) {
                log.debug("Instagram error body was not the expected JSON envelope: {}",
                        parseFailure.getMessage());
            }

            InstagramSendResult.Failure failure;
            if (code != 0 || subCode != 0) {
                failure = InstagramSendResult.classify(code, subCode);
            } else if (status == 429) {
                failure = InstagramSendResult.Failure.RATE_LIMITED;
            } else if (status >= 500) {
                failure = InstagramSendResult.Failure.TRANSIENT;
            } else {
                failure = InstagramSendResult.Failure.UNKNOWN;
            }

            // A dead token is an operator problem, not a per-message hiccup — say so loudly, since
            // nothing else in the system notices the channel has gone silent.
            if (failure.fatalForChannel()) {
                log.error("Instagram access token rejected by Meta (code {}): {}. "
                        + "The integration is down for this restaurant until the token is replaced.",
                        code, message);
            } else {
                log.warn("Instagram {} failed: status={} code={} subcode={} failure={} message={}",
                        what, status, code, subCode, failure, message);
            }
            return InstagramSendResult.failed(failure, code, message);
        }

        // Transport-level: connect/read timeout, DNS, connection reset.
        log.error("Instagram {} failed: {}", what, t.getMessage());
        return InstagramSendResult.failed(InstagramSendResult.Failure.TRANSIENT, 0, t.getMessage());
    }
}
