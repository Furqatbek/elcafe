package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.enums.InstagramInboundKind;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Classification of real Meta webhook payload shapes.
 *
 * <p>Before this existed, {@code processMessagingEvent} looked for a {@code text} field and nothing
 * else: everything that was not plain text either vanished silently or was handed to the registration
 * wizard as though the customer had typed it. Each test here is one of the shapes that used to be
 * mishandled.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramWebhookServicePayloadTest {

    private static final String ACCOUNT = "17841400000000000";
    private static final String SENDER  = "user-igsid-1";

    @Mock private InstagramBotService botService;
    @Mock private InstagramApiClient  apiClient;
    @Mock private InstagramWebhookDedupService dedupService;
    @Mock private InstagramMessageLogger messageLogger;
    @Mock private InstagramBotConfigRepository configRepository;

    @InjectMocks private InstagramWebhookService service;

    private InstagramBotConfig activeConfig() {
        return InstagramBotConfig.builder()
                .restaurantId(3L)
                .instagramAccountId(ACCOUNT)
                .isActive(true)
                .build();
    }

    /** Wrap one messaging event in the envelope Meta actually delivers. */
    private Map<String, Object> payload(Map<String, Object> messagingEvent) {
        return Map.of(
                "object", "instagram",
                "entry", List.of(Map.of("id", ACCOUNT, "messaging", List.of(messagingEvent))));
    }

    private Map<String, Object> from(String key, Object value) {
        return Map.of("sender", Map.of("id", SENDER), "recipient", Map.of("id", ACCOUNT), key, value);
    }

    private void deliver(Map<String, Object> messagingEvent) {
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(activeConfig());
        // First delivery of every mid here — dedup is exercised in its own test.
        when(dedupService.firstDelivery(any(), any())).thenReturn(true);
        service.processWebhookPayload(payload(messagingEvent));
    }

    @Test
    void plainTextReachesTheWizardAsText() {
        deliver(from("message", Map.of("mid", "m1", "text", "Salom")));

        verify(botService).handleIncomingMessage(any(), eq(SENDER), any(),
                eq(InstagramInboundKind.TEXT), eq("Salom"), isNull());
    }

    @Test
    void anEchoOfOurOwnMessageIsDropped() {
        // sender.id is the BUSINESS account on an echo. Processing it created a phantom subscriber
        // and made the bot answer itself.
        deliver(from("message", Map.of("mid", "m2", "text", "Xush kelibsiz!", "is_echo", true)));

        verify(botService, never()).handleIncomingMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void readAndDeliveryReceiptsAndReactionsAreDropped() {
        deliver(from("read", Map.of("mid", "m3")));
        deliver(from("delivery", Map.of("mids", List.of("m3"))));
        deliver(from("reaction", Map.of("mid", "m3", "action", "react", "emoji", "❤️")));

        verify(botService, never()).handleIncomingMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aStoryReplyIsClassifiedAsStoryEngagementNotWizardInput() {
        // The bug this prevents: "🔥" on a story, while the wizard was AWAITING_ADDRESS, was saved
        // as the customer's delivery address.
        deliver(from("message", Map.of(
                "mid", "m4",
                "text", "🔥",
                "reply_to", Map.of("story", Map.of("id", "story-1", "url", "https://…")))));

        verify(botService).handleIncomingMessage(any(), eq(SENDER), any(),
                eq(InstagramInboundKind.STORY_REPLY), eq("🔥"), isNull());
    }

    @Test
    void aStoryMentionIsClassifiedAsSuch() {
        deliver(from("message", Map.of(
                "mid", "m5",
                "attachments", List.of(Map.of("type", "story_mention",
                        "payload", Map.of("url", "https://…"))))));

        verify(botService).handleIncomingMessage(any(), eq(SENDER), any(),
                eq(InstagramInboundKind.STORY_MENTION), any(), isNull());
    }

    @Test
    void mediaIsClassifiedAsUnsupportedInsteadOfBeingDropped() {
        // Photos, voice notes, stickers and location shares used to be discarded before the bot was
        // called at all, leaving the customer parked mid-wizard in silence.
        deliver(from("message", Map.of(
                "mid", "m6",
                "attachments", List.of(Map.of("type", "image",
                        "payload", Map.of("url", "https://…"))))));

        verify(botService).handleIncomingMessage(any(), eq(SENDER), any(),
                eq(InstagramInboundKind.UNSUPPORTED_ATTACHMENT), any(), isNull());
    }

    @Test
    void quickRepliesAndPostbacksBothArriveAsQuickReply() {
        deliver(from("message", Map.of(
                "mid", "m7", "text", "Done",
                "quick_reply", Map.of("payload", "DONE"))));
        verify(botService).handleIncomingMessage(any(), eq(SENDER), any(),
                eq(InstagramInboundKind.QUICK_REPLY), any(), eq("DONE"));

        deliver(from("postback", Map.of("title", "Add another", "payload", "ADD_ADDRESS")));
        verify(botService).handleIncomingMessage(any(), eq(SENDER), any(),
                eq(InstagramInboundKind.QUICK_REPLY), isNull(), eq("ADD_ADDRESS"));
    }

    @Test
    void anEntryForAnUnknownAccountNeverReachesTheBot() {
        when(botService.getConfigByInstagramAccountId(any())).thenReturn(null);

        service.processWebhookPayload(payload(from("message", Map.of("mid", "m8", "text", "hi"))));

        verify(botService, never()).handleIncomingMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anInactiveConfigNeverReachesTheBot() {
        InstagramBotConfig inactive = activeConfig();
        inactive.setIsActive(false);
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(inactive);

        service.processWebhookPayload(payload(from("message", Map.of("mid", "m9", "text", "hi"))));

        verify(botService, never()).handleIncomingMessage(any(), any(), any(), any(), any(), any());
    }
}
