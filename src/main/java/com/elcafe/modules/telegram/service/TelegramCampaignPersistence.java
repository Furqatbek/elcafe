package com.elcafe.modules.telegram.service;

import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.telegram.entity.TelegramCampaign;
import com.elcafe.modules.telegram.entity.TelegramCampaignRecipient;
import com.elcafe.modules.telegram.entity.TelegramLog;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.entity.TelegramTemplate;
import com.elcafe.modules.telegram.enums.TelegramMessageType;
import com.elcafe.modules.telegram.repository.TelegramCampaignRecipientRepository;
import com.elcafe.modules.telegram.repository.TelegramLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Transactional database writes for Telegram campaign sending.
 *
 * <p>Deliberately split out of {@link TelegramCampaignExecutor}. The executor drives a long-running,
 * {@code @Async} send loop that must never hold a database transaction across the Telegram network
 * call and the inter-message rate-limit sleep. Each write here is therefore its own short transaction,
 * and — crucially — invoked through the Spring proxy: when these methods lived on the executor they
 * were called by sibling methods of the same bean, so {@code @Transactional} was silently bypassed
 * (Spring's proxy-based advice does not apply to self-invocation).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramCampaignPersistence {

    private final TelegramCampaignRecipientRepository recipientRepository;
    private final TelegramLogRepository logRepository;

    /** Mark a recipient as successfully sent, in its own transaction. */
    @Transactional
    public void markRecipientSent(TelegramCampaignRecipient recipient, Integer messageId, String message) {
        recipient.setStatus(MessageStatus.SENT);
        recipient.setSentAt(LocalDateTime.now());
        recipient.setTelegramMessageId(messageId != null ? messageId.longValue() : null);
        recipient.setMessageContent(message);
        recipientRepository.save(recipient);
    }

    /** Mark a recipient as failed, in its own transaction. */
    @Transactional
    public void markRecipientFailed(TelegramCampaignRecipient recipient, String errorMessage) {
        recipient.setStatus(MessageStatus.FAILED);
        recipient.setErrorMessage(errorMessage);
        recipientRepository.save(recipient);
    }

    /**
     * Best-effort audit log for a campaign message. Not annotated {@code @Transactional} on purpose: the
     * repository save is transactional on its own, and a logging failure must never poison the caller —
     * so we swallow it. Wrapping this in an outer transaction and swallowing would instead surface an
     * {@code UnexpectedRollbackException} at commit.
     */
    public void logCampaignMessage(TelegramCampaign campaign, TelegramCampaignRecipient recipient,
                                   String message, Integer telegramMessageId, MessageStatus status, String errorMessage) {
        try {
            TelegramSubscriber subscriber = recipient.getSubscriber();
            TelegramLog logEntry = TelegramLog.builder()
                    .restaurantId(subscriber != null ? subscriber.getRestaurantId() : null)
                    .subscriber(subscriber)
                    .telegramUserId(recipient.getTelegramUserId())
                    .username(subscriber != null ? subscriber.getUsername() : null)
                    .message(message)
                    .messageType(TelegramMessageType.CAMPAIGN)
                    .template(campaign.getTemplate())
                    .campaign(campaign)
                    .telegramMessageId(telegramMessageId != null ? telegramMessageId.longValue() : null)
                    .status(status)
                    .sentAt(status == MessageStatus.SENT ? LocalDateTime.now() : null)
                    .errorMessage(errorMessage)
                    .build();
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to log campaign message: {}", e.getMessage());
        }
    }

    /**
     * Best-effort audit log for an automation/template message. Non-transactional for the same reason as
     * {@link #logCampaignMessage}.
     */
    public void logTemplateMessage(TelegramSubscriber subscriber, TelegramTemplate template,
                                   String message, MessageStatus status, String error) {
        try {
            TelegramLog logEntry = TelegramLog.builder()
                    .restaurantId(subscriber != null ? subscriber.getRestaurantId() : null)
                    .subscriber(subscriber)
                    .telegramUserId(subscriber.getTelegramUserId())
                    .username(subscriber.getUsername())
                    .message(message)
                    .messageType(TelegramMessageType.AUTOMATION)
                    .template(template)
                    .status(status)
                    .sentAt(status == MessageStatus.SENT ? LocalDateTime.now() : null)
                    .errorMessage(error)
                    .build();
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to log template message: {}", e.getMessage());
        }
    }
}
