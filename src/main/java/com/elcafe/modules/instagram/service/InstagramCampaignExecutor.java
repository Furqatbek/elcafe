package com.elcafe.modules.instagram.service;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramCampaign;
import com.elcafe.modules.instagram.entity.InstagramCampaignRecipient;
import com.elcafe.modules.instagram.enums.InstagramMessageType;
import com.elcafe.modules.instagram.repository.InstagramCampaignRecipientRepository;
import com.elcafe.modules.instagram.repository.InstagramCampaignRepository;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.enums.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Runs one Instagram campaign to completion on an {@code @Async} thread — the replacement for the old
 * synchronous {@code InstagramBotService.broadcast} that blocked the Tomcat request thread and left no
 * record of who was messaged.
 *
 * <p>Each recipient's status is written as it is processed (via {@link InstagramCampaignPersistence}, in
 * its own committed transaction), so a re-send only touches PENDING rows — an already-SENT recipient is
 * never messaged twice. A dead token / rate-limit / open circuit halts the run and leaves the rest
 * PENDING for a later re-send, rather than firing thousands of doomed calls at Meta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramCampaignExecutor {

    private final InstagramCampaignRepository campaignRepository;
    private final InstagramCampaignRecipientRepository recipientRepository;
    private final InstagramBotService botService;
    private final InstagramApiClient apiClient;
    private final InstagramCampaignPersistence persistence;

    /** Best-effort audit trail for every per-recipient send — see the loop in {@link #execute}. */
    private final InstagramMessageLogger messageLogger;

    /**
     * Proactive pacing: cap the send rate so a large campaign stays under Meta's Instagram messaging
     * limit rather than relying on the circuit breaker to trip after the fact. Conservative by default
     * — Instagram's send limits are tighter than Telegram's 25/s. Set to 0 to disable pacing.
     */
    @Value("${instagram.campaign.messages-per-second:8}")
    private int messagesPerSecond;

    @Async
    public void executeCampaign(Long campaignId) {
        // This @Async thread carries no TenantContext. Read the campaign unscoped, then bind its
        // restaurant so every recipient query is tenant-filtered, and clear it in finally so nothing
        // leaks onto the next task on this pooled thread.
        InstagramCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            log.error("Instagram campaign not found: {}", campaignId);
            return;
        }
        TenantContext.setRestaurantId(campaign.getRestaurantId());
        try {
            execute(campaign);
        } finally {
            TenantContext.clear();
        }
    }

    private void execute(InstagramCampaign campaign) {
        Long campaignId = campaign.getId();
        if (campaign.getStatus() != CampaignStatus.SENDING) {
            log.warn("Instagram campaign {} is not SENDING (is {}) — execution skipped",
                    campaignId, campaign.getStatus());
            return;
        }

        InstagramBotConfig config = botService.getActiveConfig(campaign.getRestaurantId());
        if (config == null) {
            log.warn("No active Instagram config for restaurant {} — campaign {} cancelled",
                    campaign.getRestaurantId(), campaignId);
            campaign.setStatus(CampaignStatus.CANCELLED);
            campaignRepository.save(campaign);
            return;
        }

        List<InstagramCampaignRecipient> pending =
                recipientRepository.findByCampaignIdAndStatus(campaignId, MessageStatus.PENDING);
        log.info("Instagram campaign {} sending to {} pending recipients", campaignId, pending.size());

        long pacingDelayMs = pacingDelayMs();
        InstagramSendResult.Failure haltedBy = null;
        boolean interrupted = false;
        boolean firstSend = true;
        for (InstagramCampaignRecipient recipient : pending) {
            // Pace BETWEEN sends (never before the first, so a one-off is immediate). An interrupt —
            // an app shutdown mid-run — stops cleanly and leaves the rest PENDING for a re-send.
            if (!firstSend && pacingDelayMs > 0) {
                try {
                    sleepMillis(pacingDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    interrupted = true;
                    break;
                }
            }
            firstSend = false;

            InstagramSendResult result =
                    apiClient.sendMessage(config, recipient.getIgsid(), campaign.getMessageText());
            // Best-effort audit row for every attempt — sent, per-recipient failure, or the fatal one
            // that halts the run below — so the log is a complete record of what this campaign tried.
            // subscriber is deliberately null: recipient.getSubscriber() is a lazy association loaded in
            // an earlier, already-closed transaction, and this @Async thread holds no Hibernate session
            // to satisfy it; recipient.getIgsid() (denormalised, eager) is passed instead.
            messageLogger.record(config, recipient.getIgsid(), null, InstagramMessageType.CAMPAIGN,
                    campaign.getMessageText(), result, campaignId);
            if (result.delivered()) {
                persistence.markSent(recipient, campaign.getMessageText());
                campaign.incrementSentCount();
            } else if (isFatal(result.failure())) {
                // Do NOT consume this recipient — leave it PENDING so a re-send retries it, then stop:
                // every remaining call would fail the same way.
                haltedBy = result.failure();
                break;
            } else {
                // Per-recipient failure (blocked, outside 24h window, transient): record and move on.
                persistence.markFailed(recipient, describe(result));
                campaign.incrementFailedCount();
            }

            if ((campaign.getSentCount() + campaign.getFailedCount()) % 100 == 0) {
                campaignRepository.save(campaign);   // periodic progress checkpoint
            }
        }

        if (haltedBy != null || interrupted) {
            String reason = interrupted ? "interrupted" : haltedBy.name();
            log.warn("Instagram campaign {} halted ({}): sent={} failed={} — remaining left PENDING for re-send",
                    campaignId, reason, campaign.getSentCount(), campaign.getFailedCount());
            campaign.setStatus(CampaignStatus.CANCELLED);
        } else {
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaign.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        campaignRepository.save(campaign);
        log.info("Instagram campaign {} finished: status={} sent={} failed={}",
                campaignId, campaign.getStatus(), campaign.getSentCount(), campaign.getFailedCount());
    }

    /** Milliseconds to wait between consecutive sends; 0 disables pacing (messages-per-second ≤ 0). */
    long pacingDelayMs() {
        return messagesPerSecond > 0 ? 1000L / messagesPerSecond : 0L;
    }

    /** Extracted so tests can stub the wait instead of actually sleeping. */
    void sleepMillis(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static boolean isFatal(InstagramSendResult.Failure failure) {
        return failure == InstagramSendResult.Failure.TOKEN_INVALID
                || failure == InstagramSendResult.Failure.RATE_LIMITED
                || failure == InstagramSendResult.Failure.CIRCUIT_OPEN;
    }

    private static String describe(InstagramSendResult result) {
        String base = result.failure() != null ? result.failure().name() : "UNKNOWN";
        return result.message() != null ? base + ": " + result.message() : base;
    }
}
