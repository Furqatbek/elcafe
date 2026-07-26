package com.elcafe.modules.instagram.service;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refutes "the webhook discards entry.id, so the module is structurally single-tenant".
 *
 * <p>{@code processEntry} keys off {@code entry.id} — the IG business account that received the event —
 * to resolve that account's own config (its tenant), then binds {@link TenantContext} to that
 * restaurant for the duration of dispatch. Two restaurants can be active at once; an event for one is
 * never answered with the other's config or token, and the {@code @Async} pool thread carries the
 * correct tenant for the bot's writes (not an unset one).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramWebhookServiceMultiTenantRoutingTest {

    private static final String ACC_A = "17841400000000001";
    private static final String ACC_B = "17841400000000002";
    private static final long RESTAURANT_A = 1L;
    private static final long RESTAURANT_B = 2L;

    @Mock private InstagramBotService botService;
    @Mock private InstagramApiClient apiClient;
    @Mock private InstagramWebhookDedupService dedupService;
    @Mock private InstagramMessageLogger messageLogger;

    @InjectMocks private InstagramWebhookService service;

    private final InstagramBotConfig configA = InstagramBotConfig.builder()
            .restaurantId(RESTAURANT_A).instagramAccountId(ACC_A).isActive(true).build();
    private final InstagramBotConfig configB = InstagramBotConfig.builder()
            .restaurantId(RESTAURANT_B).instagramAccountId(ACC_B).isActive(true).build();

    @BeforeEach
    void bothTenantsAreActiveAtOnce() {
        when(botService.getConfigByInstagramAccountId(ACC_A)).thenReturn(configA);
        when(botService.getConfigByInstagramAccountId(ACC_B)).thenReturn(configB);
        // Distinct mids per sender — every event is a first delivery here; dedup has its own test.
        when(dedupService.firstDelivery(any(), any())).thenReturn(true);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private Map<String, Object> entry(String accountId, String senderIgsid) {
        return Map.of(
                "id", accountId,
                "messaging", List.of(Map.of(
                        "sender", Map.of("id", senderIgsid),
                        "message", Map.of("mid", "m-" + senderIgsid, "text", "hi"))));
    }

    private Map<String, Object> delivery(List<Map<String, Object>> entries) {
        return Map.of("object", "instagram", "entry", entries);
    }

    @Test
    @DisplayName("entry.id routes each delivery to the config that owns the receiving account")
    void entryRoutesByReceivingAccount() {
        service.processWebhookPayload(delivery(List.of(entry(ACC_A, "sender-A"))));

        verify(botService).handleIncomingMessage(eq(configA), eq("sender-A"), any(), any(), any(), any());
        verify(botService, never()).handleIncomingMessage(eq(configB), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("two restaurants stay active at once — the second's events do not get the first's config")
    void bothTenantsAreServedIndependently() {
        service.processWebhookPayload(delivery(List.of(entry(ACC_A, "sender-A"))));
        service.processWebhookPayload(delivery(List.of(entry(ACC_B, "sender-B"))));

        verify(botService).handleIncomingMessage(eq(configA), eq("sender-A"), any(), any(), any(), any());
        verify(botService).handleIncomingMessage(eq(configB), eq("sender-B"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the @Async thread carries the receiving restaurant's tenant during dispatch, cleared after")
    void tenantContextIsBoundPerEntryAndClearedAfter() {
        // Capture the thread's bound tenant at the moment each entry is dispatched, keyed by the
        // dispatched config's restaurant. This is the finding's "@Async → TenantContext unset → writes
        // unscoped" claim, made falsifiable: an unbound context would record null here.
        Map<Long, Long> boundDuringDispatch = new HashMap<>();
        doAnswer(inv -> {
            InstagramBotConfig dispatched = inv.getArgument(0);
            boundDuringDispatch.put(dispatched.getRestaurantId(), TenantContext.getRestaurantId());
            return null;
        }).when(botService).handleIncomingMessage(any(), any(), any(), any(), any(), any());

        // One delivery carrying entries for BOTH accounts: each must bind its own tenant, and the
        // context must not leak from the first entry into the second on the pooled thread.
        service.processWebhookPayload(delivery(List.of(entry(ACC_A, "sender-A"), entry(ACC_B, "sender-B"))));

        assertThat(boundDuringDispatch)
                .as("each entry's bot writes run under the receiving restaurant's own tenant")
                .containsEntry(RESTAURANT_A, RESTAURANT_A)
                .containsEntry(RESTAURANT_B, RESTAURANT_B);
        assertThat(TenantContext.getRestaurantId())
                .as("context cleared after the delivery — nothing leaks onto the next pooled task")
                .isNull();
    }
}
