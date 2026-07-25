package com.elcafe.modules.instagram.service;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramCampaign;
import com.elcafe.modules.instagram.entity.InstagramCampaignRecipient;
import com.elcafe.modules.instagram.repository.InstagramCampaignRecipientRepository;
import com.elcafe.modules.instagram.repository.InstagramCampaignRepository;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.enums.MessageStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The async campaign send loop: it messages every PENDING recipient, records each outcome, halts on a
 * channel-fatal failure while leaving the rest PENDING for a re-send, and runs under the campaign's own
 * tenant. This is what makes the engine both non-blocking and idempotent — the two things the old
 * synchronous broadcast lacked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramCampaignExecutorTest {

    private static final Long CAMPAIGN_ID = 42L;
    private static final Long TENANT = 7L;

    @Mock private InstagramCampaignRepository campaignRepository;
    @Mock private InstagramCampaignRecipientRepository recipientRepository;
    @Mock private InstagramBotService botService;
    @Mock private InstagramApiClient apiClient;
    @Mock private InstagramCampaignPersistence persistence;

    @InjectMocks private InstagramCampaignExecutor executor;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private InstagramCampaign campaign(CampaignStatus status) {
        return InstagramCampaign.builder()
                .id(CAMPAIGN_ID).restaurantId(TENANT).messageText("promo").status(status).build();
    }

    private InstagramCampaignRecipient recipient(long id, String igsid) {
        return InstagramCampaignRecipient.builder()
                .id(id).restaurantId(TENANT).igsid(igsid).status(MessageStatus.PENDING).build();
    }

    private void campaignIs(InstagramCampaign campaign) {
        when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));
        when(botService.getActiveConfig(TENANT))
                .thenReturn(InstagramBotConfig.builder().restaurantId(TENANT).isActive(true).build());
    }

    private void pending(InstagramCampaignRecipient... recipients) {
        when(recipientRepository.findByCampaignIdAndStatus(CAMPAIGN_ID, MessageStatus.PENDING))
                .thenReturn(List.of(recipients));
    }

    @Test
    @DisplayName("sends to every PENDING recipient, records each, and completes")
    void sendsAllPendingAndCompletes() {
        InstagramCampaign campaign = campaign(CampaignStatus.SENDING);
        campaignIs(campaign);
        pending(recipient(1L, "a"), recipient(2L, "b"));
        when(apiClient.sendMessage(any(), anyString(), anyString())).thenReturn(InstagramSendResult.ok());

        executor.executeCampaign(CAMPAIGN_ID);

        // Idempotency comes from only ever pulling PENDING rows — an already-SENT recipient is never here.
        verify(recipientRepository).findByCampaignIdAndStatus(CAMPAIGN_ID, MessageStatus.PENDING);
        verify(persistence, times(2)).markSent(any(), eq("promo"));
        verify(persistence, never()).markFailed(any(), anyString());
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        assertThat(campaign.getSentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a per-recipient failure is recorded and the run continues to completion")
    void perRecipientFailureIsRecordedAndRunContinues() {
        InstagramCampaign campaign = campaign(CampaignStatus.SENDING);
        campaignIs(campaign);
        pending(recipient(1L, "a"), recipient(2L, "b"));
        when(apiClient.sendMessage(any(), eq("a"), anyString())).thenReturn(InstagramSendResult.ok());
        when(apiClient.sendMessage(any(), eq("b"), anyString())).thenReturn(
                InstagramSendResult.failed(InstagramSendResult.Failure.RECIPIENT_UNAVAILABLE, 551, "blocked"));

        executor.executeCampaign(CAMPAIGN_ID);

        verify(persistence).markSent(any(), eq("promo"));
        verify(persistence).markFailed(any(), anyString());
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        assertThat(campaign.getSentCount()).isEqualTo(1);
        assertThat(campaign.getFailedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a channel-fatal failure halts the run, leaving the rest PENDING and the campaign CANCELLED")
    void fatalFailureHaltsAndLeavesRemainingPending() {
        InstagramCampaign campaign = campaign(CampaignStatus.SENDING);
        campaignIs(campaign);
        pending(recipient(1L, "a"), recipient(2L, "b"), recipient(3L, "c"));
        when(apiClient.sendMessage(any(), eq("a"), anyString())).thenReturn(InstagramSendResult.ok());
        when(apiClient.sendMessage(any(), eq("b"), anyString())).thenReturn(
                InstagramSendResult.failed(InstagramSendResult.Failure.TOKEN_INVALID, 190, "expired token"));

        executor.executeCampaign(CAMPAIGN_ID);

        verify(persistence).markSent(any(), eq("promo"));          // only the first
        verify(persistence, never()).markFailed(any(), anyString()); // the token failure is NOT consumed
        verify(apiClient, never()).sendMessage(any(), eq("c"), anyString()); // run stopped before the third
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.CANCELLED);
        assertThat(campaign.getSentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("with no active config the campaign is cancelled and nothing is sent")
    void noActiveConfigCancels() {
        InstagramCampaign campaign = campaign(CampaignStatus.SENDING);
        when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));
        when(botService.getActiveConfig(TENANT)).thenReturn(null);

        executor.executeCampaign(CAMPAIGN_ID);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.CANCELLED);
        verify(recipientRepository, never()).findByCampaignIdAndStatus(any(), any());
        verify(apiClient, never()).sendMessage(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("a campaign not in SENDING is skipped entirely")
    void notSendingIsSkipped() {
        when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign(CampaignStatus.DRAFT)));

        executor.executeCampaign(CAMPAIGN_ID);

        verify(recipientRepository, never()).findByCampaignIdAndStatus(any(), any());
        verify(apiClient, never()).sendMessage(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("the send runs under the campaign's own tenant, and the context is cleared afterwards")
    void tenantContextBoundDuringSendAndClearedAfter() {
        InstagramCampaign campaign = campaign(CampaignStatus.SENDING);
        campaignIs(campaign);
        pending(recipient(1L, "a"));
        AtomicReference<Long> boundDuringSend = new AtomicReference<>();
        when(apiClient.sendMessage(any(), anyString(), anyString())).thenAnswer(inv -> {
            boundDuringSend.set(TenantContext.getRestaurantId());
            return InstagramSendResult.ok();
        });

        executor.executeCampaign(CAMPAIGN_ID);

        assertThat(boundDuringSend.get())
                .as("the @Async send runs under the campaign's restaurant, not an unset context")
                .isEqualTo(TENANT);
        assertThat(TenantContext.getRestaurantId())
                .as("cleared after the run — nothing leaks onto the next pooled task")
                .isNull();
    }

    @Test
    @DisplayName("the pacing delay is derived from messages-per-second; 0 disables it")
    void pacingDelayIsDerivedFromRate() {
        ReflectionTestUtils.setField(executor, "messagesPerSecond", 8);
        assertThat(executor.pacingDelayMs()).isEqualTo(125L);   // 1000 / 8
        ReflectionTestUtils.setField(executor, "messagesPerSecond", 20);
        assertThat(executor.pacingDelayMs()).isEqualTo(50L);
        ReflectionTestUtils.setField(executor, "messagesPerSecond", 0);
        assertThat(executor.pacingDelayMs()).isEqualTo(0L);     // pacing off
    }

    @Test
    @DisplayName("sends are paced BETWEEN recipients — one wait per gap, never before the first")
    void sendsArePacedBetweenRecipients() throws Exception {
        // Spy so the wait is observed without actually sleeping — deterministic, not timing-based.
        InstagramCampaignExecutor paced = spy(new InstagramCampaignExecutor(
                campaignRepository, recipientRepository, botService, apiClient, persistence));
        ReflectionTestUtils.setField(paced, "messagesPerSecond", 20);   // 50ms between sends
        doNothing().when(paced).sleepMillis(anyLong());

        InstagramCampaign campaign = campaign(CampaignStatus.SENDING);
        campaignIs(campaign);
        pending(recipient(1L, "a"), recipient(2L, "b"), recipient(3L, "c"));
        when(apiClient.sendMessage(any(), anyString(), anyString())).thenReturn(InstagramSendResult.ok());

        paced.executeCampaign(CAMPAIGN_ID);

        // 3 sends → exactly 2 inter-send gaps, each the configured 50ms; no wait before the first.
        verify(paced, times(2)).sleepMillis(50L);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        assertThat(campaign.getSentCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("with pacing disabled (rate 0) no wait happens at all")
    void pacingDisabledDoesNotWait() throws Exception {
        InstagramCampaignExecutor unpaced = spy(new InstagramCampaignExecutor(
                campaignRepository, recipientRepository, botService, apiClient, persistence));
        ReflectionTestUtils.setField(unpaced, "messagesPerSecond", 0);

        InstagramCampaign campaign = campaign(CampaignStatus.SENDING);
        campaignIs(campaign);
        pending(recipient(1L, "a"), recipient(2L, "b"));
        when(apiClient.sendMessage(any(), anyString(), anyString())).thenReturn(InstagramSendResult.ok());

        unpaced.executeCampaign(CAMPAIGN_ID);

        verify(unpaced, never()).sleepMillis(anyLong());
    }
}
