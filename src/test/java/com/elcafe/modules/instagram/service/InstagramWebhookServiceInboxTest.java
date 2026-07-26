package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramInboundMessage;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.enums.InstagramInboundKind;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramInboundMessageRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V179: conversation storage + human handoff, both wired into {@link InstagramWebhookService
 * #processMessagingEvent}.
 *
 * <ul>
 *   <li>Every inbound TEXT message is persisted to {@link InstagramInboundMessage}, independent of
 *       whether the wizard ends up answering it.</li>
 *   <li>A subscriber with a future {@code humanHandoffUntil} has the wizard dispatch
 *       ({@code botService.handleIncomingMessage}) skipped, but the message is still stored — a human
 *       agent owns the thread, but the transcript keeps recording what came in.</li>
 *   <li>Handoff gating applies to every dispatch-worthy event kind, not just TEXT (a postback tap while
 *       handed off must not resurrect the wizard either).</li>
 *   <li>Everything here is best-effort: a lookup or save failure never blocks the wizard, mirroring the
 *       V177 heartbeat's identical stance ({@code InstagramWebhookServiceLastWebhookReceivedTest}).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramWebhookServiceInboxTest {

    private static final String ACCOUNT = "17841400000000000";
    private static final String SENDER  = "user-igsid-1";
    private static final long   TENANT  = 3L;

    @Mock private InstagramBotService botService;
    @Mock private InstagramApiClient apiClient;
    @Mock private InstagramWebhookDedupService dedupService;
    @Mock private InstagramMessageLogger messageLogger;
    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramInboundMessageRepository inboundMessageRepository;

    @InjectMocks private InstagramWebhookService service;

    private InstagramBotConfig activeConfig() {
        return InstagramBotConfig.builder()
                .id(42L).restaurantId(TENANT).instagramAccountId(ACCOUNT).isActive(true).build();
    }

    private InstagramSubscriber subscriber(OffsetDateTime handoffUntilOrNull) {
        return InstagramSubscriber.builder()
                .id(9L).restaurantId(TENANT).igsid(SENDER).humanHandoffUntil(handoffUntilOrNull).build();
    }

    private Map<String, Object> textDelivery(String mid, String text) {
        return Map.of("object", "instagram", "entry", List.of(Map.of(
                "id", ACCOUNT,
                "messaging", List.of(Map.of(
                        "sender", Map.of("id", SENDER),
                        "message", Map.of("mid", mid, "text", text))))));
    }

    private Map<String, Object> postbackDelivery(String mid, String payload) {
        return Map.of("object", "instagram", "entry", List.of(Map.of(
                "id", ACCOUNT,
                "messaging", List.of(Map.of(
                        "sender", Map.of("id", SENDER),
                        "postback", Map.of("mid", mid, "payload", payload))))));
    }

    private void stubActiveConfigAndDedup() {
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(activeConfig());
        when(dedupService.firstDelivery(any(), any())).thenReturn(true);
    }

    // -------------------------------------------------------------------------
    // Storage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("an inbound TEXT message is persisted with the tenant, igsid and text")
    void inboundTextMessageIsStored() {
        stubActiveConfigAndDedup();
        when(subscriberRepository.findByIgsidAndRestaurantId(SENDER, TENANT)).thenReturn(Optional.empty());
        OffsetDateTime before = OffsetDateTime.now();

        service.processWebhookPayload(textDelivery("m1", "Salom, menu bormi?"));

        ArgumentCaptor<InstagramInboundMessage> captor = ArgumentCaptor.forClass(InstagramInboundMessage.class);
        verify(inboundMessageRepository).save(captor.capture());
        InstagramInboundMessage saved = captor.getValue();
        assertThat(saved.getRestaurantId()).isEqualTo(TENANT);
        assertThat(saved.getIgsid()).isEqualTo(SENDER);
        assertThat(saved.getMessageText()).isEqualTo("Salom, menu bormi?");
        assertThat(saved.getSubscriber()).isNull(); // no subscriber row yet for a brand-new sender
        assertThat(saved.getReceivedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("an inbound TEXT message from a known subscriber links subscriber_id on the stored row")
    void inboundTextMessage_linksExistingSubscriber() {
        stubActiveConfigAndDedup();
        InstagramSubscriber existing = subscriber(null);
        when(subscriberRepository.findByIgsidAndRestaurantId(SENDER, TENANT)).thenReturn(Optional.of(existing));

        service.processWebhookPayload(textDelivery("m2", "hi"));

        ArgumentCaptor<InstagramInboundMessage> captor = ArgumentCaptor.forClass(InstagramInboundMessage.class);
        verify(inboundMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getSubscriber()).isEqualTo(existing);
    }

    @Test
    @DisplayName("a quick-reply / story / postback / unsupported-attachment event is never stored — only TEXT is")
    void onlyTextKindIsStored() {
        stubActiveConfigAndDedup();
        when(subscriberRepository.findByIgsidAndRestaurantId(any(), any())).thenReturn(Optional.empty());

        service.processWebhookPayload(postbackDelivery("m3", "ADD_ADDRESS"));

        verify(inboundMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("a storage failure never blocks the wizard dispatch that follows it")
    void storageFailure_neverBlocksDispatch() {
        stubActiveConfigAndDedup();
        when(subscriberRepository.findByIgsidAndRestaurantId(SENDER, TENANT)).thenReturn(Optional.empty());
        when(inboundMessageRepository.save(any())).thenThrow(new RuntimeException("db is on fire"));

        service.processWebhookPayload(textDelivery("m4", "hello"));

        verify(botService).handleIncomingMessage(any(), eq(SENDER), any(),
                eq(InstagramInboundKind.TEXT), eq("hello"), any());
    }

    // -------------------------------------------------------------------------
    // Human handoff
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("while handed off (future humanHandoffUntil), the wizard is NOT dispatched — but the "
            + "TEXT message IS still stored")
    void handedOff_wizardSkipped_messageStillStored() {
        stubActiveConfigAndDedup();
        InstagramSubscriber handedOff = subscriber(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        when(subscriberRepository.findByIgsidAndRestaurantId(SENDER, TENANT)).thenReturn(Optional.of(handedOff));

        service.processWebhookPayload(textDelivery("m5", "still typing to the human"));

        verify(botService, never()).handleIncomingMessage(any(), any(), any(), any(), any(), any());
        ArgumentCaptor<InstagramInboundMessage> captor = ArgumentCaptor.forClass(InstagramInboundMessage.class);
        verify(inboundMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getMessageText()).isEqualTo("still typing to the human");
        assertThat(captor.getValue().getSubscriber()).isEqualTo(handedOff);
    }

    @Test
    @DisplayName("a PAST humanHandoffUntil behaves exactly like no handoff — the wizard dispatches normally")
    void lapsedHandoff_behavesAsNoHandoff() {
        stubActiveConfigAndDedup();
        InstagramSubscriber lapsed = subscriber(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(subscriberRepository.findByIgsidAndRestaurantId(SENDER, TENANT)).thenReturn(Optional.of(lapsed));

        service.processWebhookPayload(textDelivery("m6", "hello again"));

        verify(botService).handleIncomingMessage(any(), eq(SENDER), any(),
                eq(InstagramInboundKind.TEXT), eq("hello again"), any());
    }

    @Test
    @DisplayName("a null humanHandoffUntil (never handed off) dispatches normally")
    void nullHandoff_dispatchesNormally() {
        stubActiveConfigAndDedup();
        when(subscriberRepository.findByIgsidAndRestaurantId(SENDER, TENANT))
                .thenReturn(Optional.of(subscriber(null)));

        service.processWebhookPayload(textDelivery("m7", "hello"));

        verify(botService).handleIncomingMessage(any(), eq(SENDER), any(),
                eq(InstagramInboundKind.TEXT), eq("hello"), any());
    }

    @Test
    @DisplayName("handoff also suppresses non-TEXT dispatch kinds — e.g. a postback tap while handed off")
    void handoff_suppressesPostbackDispatchToo() {
        stubActiveConfigAndDedup();
        InstagramSubscriber handedOff = subscriber(OffsetDateTime.now(ZoneOffset.UTC).plusHours(2));
        when(subscriberRepository.findByIgsidAndRestaurantId(SENDER, TENANT)).thenReturn(Optional.of(handedOff));

        service.processWebhookPayload(postbackDelivery("m8", "ADD_ADDRESS"));

        verify(botService, never()).handleIncomingMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a subscriber-lookup failure defaults to not-handed-off — the wizard still answers "
            + "rather than the customer's message going unanswered")
    void subscriberLookupFailure_defaultsToNotHandedOff() {
        stubActiveConfigAndDedup();
        when(subscriberRepository.findByIgsidAndRestaurantId(SENDER, TENANT))
                .thenThrow(new RuntimeException("db is on fire"));

        service.processWebhookPayload(textDelivery("m9", "hello"));

        verify(botService).handleIncomingMessage(any(), eq(SENDER), any(),
                eq(InstagramInboundKind.TEXT), eq("hello"), any());
    }
}
