package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramLog;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.enums.InstagramMessageType;
import com.elcafe.modules.instagram.repository.InstagramLogRepository;
import com.elcafe.modules.sms.enums.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the one {@link InstagramLog} row each Instagram send attempt leaves behind — the audit trail
 * {@code instagram_logs} (V171) exists for. Called right after {@code InstagramApiClient} returns, from
 * every send site: the DM wizard, an admin's one-off DM, a campaign broadcast, and a comment auto-reply.
 *
 * <p><b>Never throws.</b> A message already went out (or definitively failed) by the time this runs; a
 * problem writing the log row (a full disk, a DB blip) must not turn a successful send into an apparent
 * failure for the caller, nor blow up a campaign's send loop. Every path here is caught and logged at
 * warn, mirroring {@code TelegramCampaignPersistence.logCampaignMessage}.
 *
 * <p><b>Why {@code @Transactional} is safe here</b> (unlike {@code TelegramCampaignPersistence}, which
 * deliberately omits it): that class warns that swallowing an exception inside an ambient caller
 * transaction turns it into an {@code UnexpectedRollbackException} at the outer commit. Every call site
 * of {@link #record} runs with NO ambient transaction open — the wizard's DB work already committed
 * before {@code dispatch} sends and logs, {@code sendAdminMessage} and {@code processChangeEvent} are not
 * themselves {@code @Transactional}, and the campaign executor's send loop is {@code @Async}, not
 * transactional. So this method always opens its own, independent transaction; there is nothing above it
 * to poison.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstagramMessageLogger {

    private final InstagramLogRepository logRepository;

    /**
     * Build and save one log row for a completed send attempt.
     *
     * @param config            the bot config the send was made under (supplies the tenant); a null
     *                          config means there is nothing to attribute the row to, so it is skipped
     * @param igsid             the recipient's Instagram Scoped User ID, or — for a comment auto-reply,
     *                          which has no DM recipient — the comment id being replied to
     * @param subscriberOrNull  the linked subscriber, when the call site has one loaded; null is fine
     *                          (e.g. the wizard does not thread it through {@code PendingReply}, and the
     *                          campaign executor deliberately avoids a lazy fetch on its {@code @Async}
     *                          thread, where no Hibernate session is open to satisfy it)
     * @param type              what kind of send this was
     * @param content           the text that was sent (or attempted)
     * @param result            the outcome from {@code InstagramApiClient}; null is treated as "nothing
     *                          to record" rather than guessed at
     * @param campaignIdOrNull  the campaign this send belongs to, or null outside a campaign
     */
    @Transactional
    public void record(InstagramBotConfig config, String igsid, InstagramSubscriber subscriberOrNull,
                        InstagramMessageType type, String content, InstagramSendResult result,
                        Long campaignIdOrNull) {
        try {
            if (config == null || result == null) {
                log.warn("Skipping Instagram message log (igsid={}, type={}): {}",
                        igsid, type, config == null ? "no bot config" : "no send result");
                return;
            }
            boolean delivered = result.delivered();
            InstagramLog entry = InstagramLog.builder()
                    .restaurantId(config.getRestaurantId())
                    .subscriber(subscriberOrNull)
                    .igsid(igsid)
                    .messageType(type)
                    .message(content)
                    .campaignId(campaignIdOrNull)
                    .status(delivered ? MessageStatus.SENT : MessageStatus.FAILED)
                    // 0 is InstagramSendResult's "absent" sentinel, not a real code — leave it null
                    // rather than record a misleading "error code 0" on a successful send.
                    .errorMessage(delivered ? null : result.message())
                    .errorCode(delivered ? null : result.code())
                    .build();
            logRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to record Instagram message log (igsid={}, type={}): {}",
                    igsid, type, e.getMessage(), e);
        }
    }
}
