package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.entity.InstagramCampaignRecipient;
import com.elcafe.modules.instagram.repository.InstagramCampaignRecipientRepository;
import com.elcafe.modules.sms.enums.MessageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Short, proxied transactions for per-recipient status writes during a campaign send.
 *
 * <p>Split out of {@link InstagramCampaignExecutor} for the same two reasons the Telegram sibling was:
 * the {@code @Async} send loop must not hold a DB transaction across the Graph HTTP call, and a
 * {@code @Transactional} method invoked from a sibling method of the same bean is silently bypassed
 * (Spring's proxy advice does not apply to self-invocation). Each write here is its own committed
 * transaction, so a crash mid-run still leaves an accurate record of who was already messaged — which
 * is what lets a re-send skip them.
 */
@Component
@RequiredArgsConstructor
public class InstagramCampaignPersistence {

    private final InstagramCampaignRecipientRepository recipientRepository;

    @Transactional
    public void markSent(InstagramCampaignRecipient recipient, String message) {
        recipient.setStatus(MessageStatus.SENT);
        recipient.setSentAt(OffsetDateTime.now(ZoneOffset.UTC));
        recipient.setMessageContent(message);
        recipientRepository.save(recipient);
    }

    @Transactional
    public void markFailed(InstagramCampaignRecipient recipient, String errorMessage) {
        recipient.setStatus(MessageStatus.FAILED);
        recipient.setErrorMessage(errorMessage);
        recipientRepository.save(recipient);
    }
}
