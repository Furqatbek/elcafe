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
 * - Configure the DM thread's persistent menu and ice breakers (first-contact UX, no messaging
 *   window required — see the "Messenger profile" section below)
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

    /**
     * Global kill switch. When {@code instagram.enabled=false}, every outbound Graph call short-circuits
     * to a failed result WITHOUT hitting Meta — the incident off-switch (leaked token, Meta ban, runaway
     * auto-reply) that does not require logging into the SPA to deactivate each config, matching
     * {@code telegram.bot.enabled} / {@code sms.enabled}. The {@code = true} initializer is the default
     * outside Spring (e.g. unit tests); {@code @Value} overrides it from configuration. Defaults true.
     */
    @Value("${instagram.enabled:true}")
    private boolean enabled = true;

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
        if (!enabled) {
            return disabledResult();
        }
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
    // Private replies (from comments)
    // -------------------------------------------------------------------------

    /**
     * Send a Meta "private reply" DM against a comment — the "comment {keyword} and we'll DM you"
     * growth mechanic ({@code InstagramWebhookService}). Unlike {@link #replyToComment}, which posts a
     * PUBLIC reply under the comment, this opens a fresh 24-hour DM messaging window with whoever left
     * it. Same endpoint shape as {@link #sendMessage} ({@code {instagramAccountId}/messages}) — only the
     * {@code recipient} object differs, so this shares {@link #postToMessagesApi}, kill switch included:
     * unlike {@link #replyToComment} (which builds its URL inline and so checks {@code enabled} itself),
     * this method has no need for its own copy of that check.
     *
     * @param config     active bot config (provides access token + account id)
     * @param commentId  numeric comment ID from the webhook event — becomes {@code recipient.comment_id}
     * @param message    DM text (the caller has already substituted any {@code {code}} placeholder)
     */
    @CircuitBreaker(name = "instagram", fallbackMethod = "sendPrivateReplyFallback")
    public InstagramSendResult sendPrivateReply(InstagramBotConfig config, String commentId, String message) {
        // commentId is public-webhook input, same as replyToComment's. It only ever lands in the JSON
        // body here (recipient.comment_id), never the URL, so the path-injection risk that motivates
        // replyToComment's check does not directly apply — but validating it identically anyway means
        // nobody has to reason, call site by call site, about whether a particular use of a
        // webhook-sourced id happens to be safe to skip. It also rejects garbage before it costs a
        // circuit-breaker-counted round trip to Meta for a request that could only ever 400.
        //
        // No explicit `if (!enabled)` guard here — postToMessagesApi below already checks it before
        // building any URL, and this method has no ordering-sensitive logic of its own that needs the
        // kill switch checked any earlier (contrast replyToComment, which builds its URL inline and so
        // carries its own copy of the same check).
        if (commentId == null || !GRAPH_ID.matcher(commentId).matches()) {
            log.warn("Refusing Instagram private reply: malformed comment id");
            return InstagramSendResult.failed(
                    InstagramSendResult.Failure.INVALID_REQUEST, 0, "malformed comment id");
        }
        Map<String, Object> body = Map.of(
                "recipient", Map.of("comment_id", commentId),
                "message",   Map.of("text", message)
        );
        return postToMessagesApi(config, body);
    }

    // -------------------------------------------------------------------------
    // Messenger profile (persistent menu / ice breakers)
    //
    // Unlike every send above, these configure the DM THREAD ITSELF rather than sending a message —
    // Meta renders the persistent menu and ice breakers before the visitor has ever messaged the
    // business, so neither call needs (or is limited by) a 24-hour messaging window. Both POST to a
    // different Graph edge ({instagramAccountId}/messenger_profile, not .../messages), so they cannot
    // share postToMessagesApi — but they replicate its exact discipline: kill switch, bearer auth,
    // account-id validation, and their own @CircuitBreaker + fallback pair.
    //
    // Default content (what to actually put in the menu/ice-breakers) is deliberately NOT here: these
    // two methods just push whatever items the caller supplies. InstagramBotConfigService owns the
    // default Uzbek content and decides when to call these (config activation) — see its javadoc for
    // why: folding a convenience "push the default profile" method into THIS class would have it call
    // these two via `this.`, which — like the postJson-wrapping trick this class's own class javadoc
    // already warns against — bypasses the Spring AOP proxy and silently drops the circuit breaker.
    // -------------------------------------------------------------------------

    /**
     * Set the persistent menu shown under the DM composer on the Instagram business account's thread.
     * Meta keeps {@code persistent_menu} and {@code ice_breakers} on the same shared
     * "messenger_profile" settings surface it has used since before Instagram Direct had its own Graph
     * edge, so the {@code platform} discriminator is included to scope these settings to Instagram
     * rather than a linked Facebook Page's Messenger — unlike {@link #sendMessage} and
     * {@link #sendPrivateReply}, whose {@code {instagramAccountId}/messages} edge needs no such
     * discriminator because the id in the path is already unambiguous. If a live check against a
     * current Meta app shows {@code platform} is ignored/rejected for this edge, dropping it is a
     * one-line change confined to this method and {@link #setIceBreakers}.
     *
     * @param config          active bot config (provides access token + account id)
     * @param callToActions   menu buttons, each {@code type}/{@code title}/{@code payload} (postback) or
     *                        {@code type}/{@code title}/{@code url} (web_url); Meta allows at most 3
     *                        top-level items without nesting a submenu
     */
    @CircuitBreaker(name = "instagram", fallbackMethod = "setPersistentMenuFallback")
    public InstagramSendResult setPersistentMenu(InstagramBotConfig config, List<Map<String, String>> callToActions) {
        List<Map<String, Object>> menu = List.of(Map.<String, Object>of(
                "locale", "default",
                "composer_input_disabled", false,
                "call_to_actions", callToActions
        ));
        Map<String, Object> body = Map.of(
                "platform", "instagram",
                "persistent_menu", menu
        );
        return postToMessengerProfile(config, body);
    }

    /**
     * Set the ice-breaker questions Meta offers a first-time visitor before they type anything.
     * See {@link #setPersistentMenu} for why {@code platform: "instagram"} is included.
     *
     * @param config       active bot config (provides access token + account id)
     * @param iceBreakers  questions, each a {@code question}/{@code payload} map; Meta allows at most 4
     */
    @CircuitBreaker(name = "instagram", fallbackMethod = "setIceBreakersFallback")
    public InstagramSendResult setIceBreakers(InstagramBotConfig config, List<Map<String, String>> iceBreakers) {
        List<Map<String, Object>> wrapped = List.of(Map.<String, Object>of(
                "locale", "default",
                "call_to_actions", iceBreakers
        ));
        Map<String, Object> body = Map.of(
                "platform", "instagram",
                "ice_breakers", wrapped
        );
        return postToMessengerProfile(config, body);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Kill-switch result: a non-retryable failure that never touched Meta. */
    private static InstagramSendResult disabledResult() {
        log.debug("Instagram integration disabled (instagram.enabled=false) — outbound send suppressed");
        return InstagramSendResult.failed(
                InstagramSendResult.Failure.INVALID_REQUEST, 0, "instagram integration disabled");
    }

    private InstagramSendResult postToMessagesApi(InstagramBotConfig config, Map<String, Object> body) {
        if (!enabled) {
            return disabledResult();
        }
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

    /** Same kill-switch + account-id-validation discipline as {@link #postToMessagesApi}, different edge. */
    private InstagramSendResult postToMessengerProfile(InstagramBotConfig config, Map<String, Object> body) {
        if (!enabled) {
            return disabledResult();
        }
        String accountId = config.getInstagramAccountId();
        if (accountId == null || !GRAPH_ID.matcher(accountId).matches()) {
            log.warn("Refusing Instagram messenger_profile update: malformed instagram account id");
            return InstagramSendResult.failed(
                    InstagramSendResult.Failure.INVALID_REQUEST, 0, "malformed instagram account id");
        }
        String url = UriComponentsBuilder.fromHttpUrl(graphBase)
                .pathSegment(accountId, "messenger_profile")
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

    @SuppressWarnings("unused")
    private InstagramSendResult sendPrivateReplyFallback(InstagramBotConfig config, String commentId,
                                                          String message, CallNotPermittedException e) {
        log.warn("Instagram circuit open, dropping private reply to comment {}", commentId);
        return InstagramSendResult.failed(InstagramSendResult.Failure.CIRCUIT_OPEN, 0, "circuit open");
    }

    @SuppressWarnings("unused")
    private InstagramSendResult sendPrivateReplyFallback(InstagramBotConfig config, String commentId,
                                                          String message, Throwable t) {
        return describe(t, "private reply to comment " + commentId);
    }

    @SuppressWarnings("unused")
    private InstagramSendResult setPersistentMenuFallback(InstagramBotConfig config,
                                                           List<Map<String, String>> callToActions,
                                                           CallNotPermittedException e) {
        log.warn("Instagram circuit open, dropping persistent_menu update for account {}",
                config.getInstagramAccountId());
        return InstagramSendResult.failed(InstagramSendResult.Failure.CIRCUIT_OPEN, 0, "circuit open");
    }

    @SuppressWarnings("unused")
    private InstagramSendResult setPersistentMenuFallback(InstagramBotConfig config,
                                                           List<Map<String, String>> callToActions,
                                                           Throwable t) {
        return describe(t, "persistent_menu update for account " + config.getInstagramAccountId());
    }

    @SuppressWarnings("unused")
    private InstagramSendResult setIceBreakersFallback(InstagramBotConfig config,
                                                        List<Map<String, String>> iceBreakers,
                                                        CallNotPermittedException e) {
        log.warn("Instagram circuit open, dropping ice_breakers update for account {}",
                config.getInstagramAccountId());
        return InstagramSendResult.failed(InstagramSendResult.Failure.CIRCUIT_OPEN, 0, "circuit open");
    }

    @SuppressWarnings("unused")
    private InstagramSendResult setIceBreakersFallback(InstagramBotConfig config,
                                                        List<Map<String, String>> iceBreakers,
                                                        Throwable t) {
        return describe(t, "ice_breakers update for account " + config.getInstagramAccountId());
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
