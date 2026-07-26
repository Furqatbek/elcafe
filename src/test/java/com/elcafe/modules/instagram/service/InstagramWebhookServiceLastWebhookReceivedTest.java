package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the V177 "Meta is delivering webhooks" heartbeat: {@link InstagramWebhookService} stamps
 * {@code last_webhook_received_at} once per processed inbound entry via
 * {@link InstagramBotConfigRepository#updateLastWebhookReceivedAt}, giving the connection-test/health
 * UI a "last heard from Meta: ..." signal alongside V175's {@code tokenHealthy}.
 *
 * <p>Deliberately does NOT test Meta's GET hub-challenge ({@code InstagramWebhookController#verify}):
 * that method never calls into {@link InstagramWebhookService} at all — it resolves the config directly
 * off {@code InstagramBotService#getConfigByVerifyToken} and returns — so it is structurally incapable
 * of reaching {@link InstagramWebhookService#stampLastWebhookReceivedBestEffort}. Only a genuine POST
 * entry, routed through {@link InstagramWebhookService#processWebhookPayload}, can stamp the heartbeat;
 * that is what every case below exercises.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramWebhookServiceLastWebhookReceivedTest {

    private static final String ACCOUNT = "17841400000000000";
    private static final long   CONFIG_ID = 42L;
    private static final long   TENANT = 3L;

    @Mock private InstagramBotService botService;
    @Mock private InstagramApiClient apiClient;
    @Mock private InstagramWebhookDedupService dedupService;
    @Mock private InstagramMessageLogger messageLogger;
    @Mock private InstagramBotConfigRepository configRepository;

    @InjectMocks private InstagramWebhookService service;

    private InstagramBotConfig activeConfig() {
        return InstagramBotConfig.builder()
                .id(CONFIG_ID).restaurantId(TENANT).instagramAccountId(ACCOUNT).isActive(true)
                .build();
    }

    private Map<String, Object> messageDelivery(String... mids) {
        List<Map<String, Object>> events = java.util.Arrays.stream(mids)
                .map(mid -> Map.<String, Object>of(
                        "sender", Map.of("id", "user-" + mid),
                        "message", Map.of("mid", mid, "text", "hi")))
                .toList();
        return Map.of("object", "instagram", "entry", List.of(Map.of("id", ACCOUNT, "messaging", events)));
    }

    // -------------------------------------------------------------------------
    // The core guard: a processed entry stamps the heartbeat with "now", keyed by config id
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a processed entry under an active config stamps last_webhook_received_at")
    void processedEntryStampsTheHeartbeat() {
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(activeConfig());
        when(dedupService.firstDelivery(any(), any())).thenReturn(true);
        OffsetDateTime before = OffsetDateTime.now();

        service.processWebhookPayload(messageDelivery("m1"));

        ArgumentCaptor<OffsetDateTime> stamped = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(configRepository).updateLastWebhookReceivedAt(eq(CONFIG_ID), stamped.capture());
        assertThat(ChronoUnit.SECONDS.between(before, stamped.getValue())).isBetween(0L, 10L);
    }

    // -------------------------------------------------------------------------
    // Write-per-event-storm guard: many events in ONE entry stamp exactly once, not once per event
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("multiple messaging events within one entry stamp the heartbeat exactly once, not once per event")
    void manyEventsInOneEntryStampOnlyOnce() {
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(activeConfig());
        when(dedupService.firstDelivery(any(), any())).thenReturn(true);

        service.processWebhookPayload(messageDelivery("m1", "m2", "m3"));

        verify(configRepository, times(1)).updateLastWebhookReceivedAt(eq(CONFIG_ID), any());
    }

    @Test
    @DisplayName("two separate deliveries (two POSTs) stamp the heartbeat twice — once each")
    void twoSeparateDeliveriesStampTwice() {
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(activeConfig());
        when(dedupService.firstDelivery(any(), any())).thenReturn(true);

        service.processWebhookPayload(messageDelivery("m1"));
        service.processWebhookPayload(messageDelivery("m2"));

        verify(configRepository, times(2)).updateLastWebhookReceivedAt(eq(CONFIG_ID), any());
    }

    // -------------------------------------------------------------------------
    // Never stamp for an entry that is dropped before real processing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("an entry for an unknown account never stamps")
    void unknownAccountNeverStamps() {
        when(botService.getConfigByInstagramAccountId(any())).thenReturn(null);

        service.processWebhookPayload(messageDelivery("m1"));

        verify(configRepository, never()).updateLastWebhookReceivedAt(anyLong(), any());
    }

    @Test
    @DisplayName("an entry for an inactive config never stamps")
    void inactiveConfigNeverStamps() {
        InstagramBotConfig inactive = activeConfig();
        inactive.setIsActive(false);
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(inactive);

        service.processWebhookPayload(messageDelivery("m1"));

        verify(configRepository, never()).updateLastWebhookReceivedAt(anyLong(), any());
    }

    // -------------------------------------------------------------------------
    // Best-effort: a failure stamping the heartbeat must never block real message dispatch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a DB blip stamping the heartbeat never blocks the actual message dispatch")
    void stampFailureNeverBlocksDispatch() {
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(activeConfig());
        when(dedupService.firstDelivery(any(), any())).thenReturn(true);
        when(configRepository.updateLastWebhookReceivedAt(anyLong(), any()))
                .thenThrow(new RuntimeException("db is on fire"));

        // Must not throw, and the real payload below must still reach the bot.
        service.processWebhookPayload(messageDelivery("m1"));

        verify(botService).handleIncomingMessage(any(), eq("user-m1"), any(), any(), eq("hi"), any());
    }
}
