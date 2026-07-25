package com.elcafe.modules.telegram.service;

import com.elcafe.modules.notification.service.TelegramBotService;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.telegram.entity.TelegramCampaign;
import com.elcafe.modules.telegram.entity.TelegramCampaignRecipient;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.repository.TelegramCampaignRecipientRepository;
import com.elcafe.modules.telegram.repository.TelegramCampaignRepository;
import com.elcafe.modules.telegram.repository.TelegramTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TelegramCampaignExecutor}, pinning the two behaviours the E5 refactor added:
 * <ul>
 *   <li>the send loop reads recipients via the subscriber-fetch-join query (not the lazy variant), so it
 *       does not depend on {@code enable_lazy_load_no_trans} on the @Async thread; and</li>
 *   <li>every database write is delegated to the proxied {@link TelegramCampaignPersistence} bean, so the
 *       {@code @Transactional} boundaries are real rather than self-invoked and silently bypassed.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TelegramCampaignExecutorTest {

    @Mock private TelegramBotService botService;
    @Mock private TelegramCampaignRepository campaignRepository;
    @Mock private TelegramCampaignRecipientRepository recipientRepository;
    @Mock private TelegramTemplateRepository templateRepository;
    @Mock private TelegramCampaignPersistence persistence;

    @InjectMocks private TelegramCampaignExecutor executor;

    private TelegramCampaignRecipient recipient(long chatId, String firstName) {
        // getDisplayName() derives from firstName/username/telegramUserId (it does not read the stored
        // display_name field), so drive the {name} placeholder via firstName for a deterministic render.
        TelegramSubscriber subscriber = TelegramSubscriber.builder()
                .telegramUserId(chatId)
                .firstName(firstName)
                .build();
        return TelegramCampaignRecipient.builder()
                .telegramUserId(chatId)
                .subscriber(subscriber)
                .status(MessageStatus.PENDING)
                .build();
    }

    private TelegramCampaign sendingCampaign() {
        return TelegramCampaign.builder()
                .restaurantId(1L)
                .status(CampaignStatus.SENDING)
                .customMessage("Hello {name}")
                .build();
    }

    @Test
    void executeCampaign_fetchesRecipientsWithSubscriber_notTheLazyQuery() {
        TelegramCampaign campaign = sendingCampaign();
        when(campaignRepository.findByIdWithTemplate(1L)).thenReturn(campaign);
        when(recipientRepository.findByCampaignIdAndStatusWithSubscriber(1L, MessageStatus.PENDING))
                .thenReturn(List.of(recipient(123L, "Ali")));
        when(botService.sendMessage(1L, 123L, "Hello Ali")).thenReturn(999);

        executor.executeCampaign(1L);

        // The fetch-join query is the one used; the lazy derived query must never be hit here.
        verify(recipientRepository).findByCampaignIdAndStatusWithSubscriber(1L, MessageStatus.PENDING);
        verify(recipientRepository, never()).findByCampaignIdAndStatus(eq(1L), eq(MessageStatus.PENDING));
    }

    @Test
    void executeCampaign_success_delegatesSentWritesToPersistenceBean() {
        TelegramCampaign campaign = sendingCampaign();
        TelegramCampaignRecipient recipient = recipient(123L, "Ali");
        when(campaignRepository.findByIdWithTemplate(1L)).thenReturn(campaign);
        when(recipientRepository.findByCampaignIdAndStatusWithSubscriber(1L, MessageStatus.PENDING))
                .thenReturn(List.of(recipient));
        when(botService.sendMessage(1L, 123L, "Hello Ali")).thenReturn(999);

        executor.executeCampaign(1L);

        verify(persistence).markRecipientSent(recipient, 999, "Hello Ali");
        verify(persistence).logCampaignMessage(campaign, recipient, "Hello Ali", 999, MessageStatus.SENT, null);
        verify(persistence, never()).markRecipientFailed(eq(recipient), eq("Failed to send message"));
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        assertThat(campaign.getSentCount()).isEqualTo(1);
        verify(campaignRepository).save(campaign);
    }

    @Test
    void executeCampaign_sendReturnsNull_delegatesFailureWritesToPersistenceBean() {
        TelegramCampaign campaign = sendingCampaign();
        TelegramCampaignRecipient recipient = recipient(123L, "Ali");
        when(campaignRepository.findByIdWithTemplate(1L)).thenReturn(campaign);
        when(recipientRepository.findByCampaignIdAndStatusWithSubscriber(1L, MessageStatus.PENDING))
                .thenReturn(List.of(recipient));
        when(botService.sendMessage(1L, 123L, "Hello Ali")).thenReturn(null);

        executor.executeCampaign(1L);

        verify(persistence).markRecipientFailed(recipient, "Failed to send message");
        verify(persistence).logCampaignMessage(campaign, recipient, "Hello Ali", null, MessageStatus.FAILED, "Failed to send message");
        verify(persistence, never()).markRecipientSent(eq(recipient), eq(999), eq("Hello Ali"));
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        assertThat(campaign.getFailedCount()).isEqualTo(1);
    }

    @Test
    void executeCampaign_notInSendingStatus_doesNothing() {
        TelegramCampaign draft = TelegramCampaign.builder().status(CampaignStatus.DRAFT).build();
        when(campaignRepository.findByIdWithTemplate(1L)).thenReturn(draft);

        executor.executeCampaign(1L);

        verifyNoInteractions(botService);
        verifyNoInteractions(persistence);
        verify(recipientRepository, never()).findByCampaignIdAndStatusWithSubscriber(eq(1L), eq(MessageStatus.PENDING));
    }
}
