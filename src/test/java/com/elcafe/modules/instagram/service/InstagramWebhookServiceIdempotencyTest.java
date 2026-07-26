package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.enums.InstagramInboundKind;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins webhook event idempotency.
 *
 * <p>Meta delivers webhooks at-least-once and re-sends on any non-2xx or timeout, so the same event
 * can arrive twice. Without dedup, a re-delivered message re-advances the registration wizard (or
 * re-runs the customer link) and a re-delivered comment re-fires the public auto-reply. The service
 * asks {@link InstagramWebhookDedupService#firstDelivery} — keyed on the tenant and the event's Meta id
 * — BEFORE dispatch, and drops the event when it returns {@code false}. Delete either guard and the
 * "exactly once" assertions here go red; that is the regression this pins. The key is namespaced
 * ({@code msg:} / {@code cmt:}) so a numeric comment id cannot collide with a message id.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramWebhookServiceIdempotencyTest {

    private static final String ACCOUNT = "17841400000000000";
    private static final String SENDER  = "user-igsid-1";
    private static final long   TENANT  = 3L;

    @Mock private InstagramBotService botService;
    @Mock private InstagramApiClient  apiClient;
    @Mock private InstagramWebhookDedupService dedupService;
    @Mock private InstagramMessageLogger messageLogger;
    @Mock private InstagramBotConfigRepository configRepository;

    @InjectMocks private InstagramWebhookService service;

    private InstagramBotConfig config() {
        return InstagramBotConfig.builder()
                .restaurantId(TENANT).instagramAccountId(ACCOUNT).isActive(true)
                .autoReplyEnabled(true).autoReplyTemplate("Thanks {comment}!")
                .build();
    }

    private Map<String, Object> messageDelivery(String mid, String text) {
        return Map.of("object", "instagram", "entry", List.of(Map.of(
                "id", ACCOUNT,
                "messaging", List.of(Map.of(
                        "sender", Map.of("id", SENDER),
                        "recipient", Map.of("id", ACCOUNT),
                        "message", Map.of("mid", mid, "text", text))))));
    }

    private Map<String, Object> commentDelivery(String commentId, String text) {
        return Map.of("object", "instagram", "entry", List.of(Map.of(
                "id", ACCOUNT,
                "changes", List.of(Map.of(
                        "field", "comments",
                        "value", Map.of("id", commentId, "text", text))))));
    }

    @Test
    @DisplayName("a re-delivered message mid is handed to the bot exactly once")
    void aRedeliveredMessageMidIsProcessedExactlyOnce() {
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(config());
        // First delivery of "msg:m-1" records and returns true; the re-delivery is a duplicate.
        when(dedupService.firstDelivery(eq(TENANT), eq("msg:m-1"))).thenReturn(true, false);

        service.processWebhookPayload(messageDelivery("m-1", "Salom"));   // first delivery
        service.processWebhookPayload(messageDelivery("m-1", "Salom"));   // Meta re-delivery

        verify(botService, times(1)).handleIncomingMessage(any(), eq(SENDER), any(),
                eq(InstagramInboundKind.TEXT), eq("Salom"), any());
        // The guard is consulted on BOTH deliveries — the second is what gets rejected.
        verify(dedupService, times(2)).firstDelivery(eq(TENANT), eq("msg:m-1"));
    }

    @Test
    @DisplayName("a re-delivered comment id fires the auto-reply exactly once")
    void aRedeliveredCommentIdRepliesExactlyOnce() {
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(config());
        when(dedupService.firstDelivery(eq(TENANT), eq("cmt:99"))).thenReturn(true, false);

        service.processWebhookPayload(commentDelivery("99", "nice"));   // first delivery
        service.processWebhookPayload(commentDelivery("99", "nice"));   // Meta re-delivery

        verify(apiClient, times(1)).replyToComment(any(), eq("99"), any());
        verify(dedupService, times(2)).firstDelivery(eq(TENANT), eq("cmt:99"));
    }

    @Test
    @DisplayName("two genuinely different mids both process — dedup does not swallow distinct events")
    void twoDistinctMidsBothProcess() {
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(config());
        when(dedupService.firstDelivery(any(), any())).thenReturn(true);

        service.processWebhookPayload(messageDelivery("m-1", "one"));
        service.processWebhookPayload(messageDelivery("m-2", "two"));

        verify(botService, times(2)).handleIncomingMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the tenant is part of the dedup key — the same mid under two accounts is not a duplicate")
    void dedupKeyCarriesTheReceivingRestaurant() {
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(config());
        when(dedupService.firstDelivery(any(), any())).thenReturn(true);

        service.processWebhookPayload(messageDelivery("m-1", "hi"));

        // Keyed on the receiving restaurant, so tenant B's identical mid is a distinct key.
        verify(dedupService).firstDelivery(eq(TENANT), eq("msg:m-1"));
    }
}
