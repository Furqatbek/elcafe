package com.elcafe.modules.telegram.service;

import com.elcafe.modules.notification.service.TelegramBotService;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.telegram.entity.*;
import com.elcafe.modules.telegram.enums.TelegramMessageType;
import com.elcafe.modules.telegram.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for executing Telegram marketing campaigns.
 * Handles message sending, rate limiting, status tracking, and logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramCampaignExecutor {

    private final TelegramBotService botService;
    private final TelegramCampaignRepository campaignRepository;
    private final TelegramCampaignRecipientRepository recipientRepository;
    private final TelegramLogRepository logRepository;
    private final TelegramTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    // Telegram rate limit: 30 messages per second, we'll be conservative with 25
    private static final int MESSAGES_PER_SECOND = 25;
    private static final long RATE_LIMIT_DELAY_MS = 1000 / MESSAGES_PER_SECOND; // ~40ms between messages

    /**
     * Execute a campaign by sending messages to all pending recipients.
     * This method runs asynchronously.
     *
     * @param campaignId The campaign ID to execute
     */
    @Async
    public void executeCampaign(Long campaignId) {
        log.info("Starting campaign execution: campaignId={}", campaignId);

        TelegramCampaign campaign = campaignRepository.findByIdWithTemplate(campaignId);
        if (campaign == null) {
            log.error("Campaign not found: {}", campaignId);
            return;
        }

        if (campaign.getStatus() != CampaignStatus.SENDING) {
            log.warn("Campaign {} is not in SENDING status, current status: {}", campaignId, campaign.getStatus());
            return;
        }

        try {
            // Get all pending recipients
            List<TelegramCampaignRecipient> recipients = recipientRepository
                    .findByCampaignIdAndStatus(campaignId, MessageStatus.PENDING);

            log.info("Campaign {} has {} pending recipients", campaignId, recipients.size());

            int sentCount = 0;
            int failedCount = 0;

            for (TelegramCampaignRecipient recipient : recipients) {
                try {
                    boolean success = sendToRecipient(campaign, recipient);

                    if (success) {
                        sentCount++;
                        campaign.incrementSentCount();
                    } else {
                        failedCount++;
                        campaign.incrementFailedCount();
                    }

                    // Save campaign progress periodically (every 100 messages)
                    if ((sentCount + failedCount) % 100 == 0) {
                        campaignRepository.save(campaign);
                    }

                    // Rate limiting
                    Thread.sleep(RATE_LIMIT_DELAY_MS);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Campaign execution interrupted: {}", campaignId);
                    break;
                } catch (Exception e) {
                    log.error("Error sending to recipient {}: {}", recipient.getTelegramUserId(), e.getMessage());
                    markRecipientFailed(recipient, e.getMessage());
                    failedCount++;
                    campaign.incrementFailedCount();
                }
            }

            // Mark campaign as completed
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaign.setCompletedAt(LocalDateTime.now());
            campaignRepository.save(campaign);

            log.info("Campaign {} completed: sent={}, failed={}", campaignId, sentCount, failedCount);

        } catch (Exception e) {
            log.error("Campaign execution failed: campaignId={}, error={}", campaignId, e.getMessage());
            campaign.setStatus(CampaignStatus.CANCELLED);
            campaignRepository.save(campaign);
        }
    }

    /**
     * Send a message to a single recipient
     */
    private boolean sendToRecipient(TelegramCampaign campaign, TelegramCampaignRecipient recipient) {
        Long chatId = recipient.getTelegramUserId();
        String message = buildMessage(campaign, recipient);
        List<List<Map<String, String>>> buttons = parseButtons(campaign);
        String imageUrl = campaign.getImageUrl();

        Integer messageId;

        // Send based on content type
        if (imageUrl != null && !imageUrl.isEmpty()) {
            if (buttons != null && !buttons.isEmpty()) {
                messageId = botService.sendPhotoWithButtons(chatId, imageUrl, message, buttons);
            } else {
                messageId = botService.sendPhoto(chatId, imageUrl, message);
            }
        } else {
            if (buttons != null && !buttons.isEmpty()) {
                messageId = botService.sendMessageWithButtons(chatId, message, buttons);
            } else {
                messageId = botService.sendMessage(chatId, message);
            }
        }

        if (messageId != null) {
            markRecipientSent(recipient, messageId, message);
            logMessage(campaign, recipient, message, messageId, MessageStatus.SENT, null);
            return true;
        } else {
            markRecipientFailed(recipient, "Failed to send message");
            logMessage(campaign, recipient, message, null, MessageStatus.FAILED, "Failed to send message");
            return false;
        }
    }

    /**
     * Build the message content for a recipient
     */
    private String buildMessage(TelegramCampaign campaign, TelegramCampaignRecipient recipient) {
        // If campaign has a custom message, use it
        if (campaign.getCustomMessage() != null && !campaign.getCustomMessage().isEmpty()) {
            return renderPlaceholders(campaign.getCustomMessage(), recipient);
        }

        // If campaign has a template, render it
        if (campaign.getTemplate() != null) {
            return renderPlaceholders(campaign.getTemplate().getContent(), recipient);
        }

        return "Message from ElCafe";
    }

    /**
     * Render placeholders in message text
     */
    private String renderPlaceholders(String text, TelegramCampaignRecipient recipient) {
        if (text == null) return "";

        TelegramSubscriber subscriber = recipient.getSubscriber();
        if (subscriber == null) return text;

        String result = text;
        result = result.replace("{name}", getDisplayName(subscriber));
        result = result.replace("{first_name}", subscriber.getFirstName() != null ? subscriber.getFirstName() : "");
        result = result.replace("{last_name}", subscriber.getLastName() != null ? subscriber.getLastName() : "");
        result = result.replace("{username}", subscriber.getUsername() != null ? "@" + subscriber.getUsername() : "");

        return result;
    }

    private String getDisplayName(TelegramSubscriber subscriber) {
        if (subscriber.getFirstName() != null) {
            return subscriber.getFirstName();
        }
        if (subscriber.getUsername() != null) {
            return subscriber.getUsername();
        }
        return "Hurmatli mijoz";
    }

    /**
     * Get buttons configuration from campaign (already parsed as List)
     */
    private List<List<Map<String, String>>> parseButtons(TelegramCampaign campaign) {
        List<Map<String, String>> buttonsConfig = campaign.getButtonsConfig();
        if (buttonsConfig == null || buttonsConfig.isEmpty()) {
            // Check template buttons
            if (campaign.getTemplate() != null && campaign.getTemplate().getButtonsConfig() != null) {
                buttonsConfig = campaign.getTemplate().getButtonsConfig();
            }
        }

        if (buttonsConfig == null || buttonsConfig.isEmpty()) {
            return null;
        }

        // Wrap single row of buttons as a list of rows
        return List.of(buttonsConfig);
    }

    /**
     * Mark recipient as sent
     */
    @Transactional
    public void markRecipientSent(TelegramCampaignRecipient recipient, Integer messageId, String message) {
        recipient.setStatus(MessageStatus.SENT);
        recipient.setSentAt(LocalDateTime.now());
        recipient.setTelegramMessageId(messageId != null ? messageId.longValue() : null);
        recipient.setMessageContent(message);
        recipientRepository.save(recipient);
    }

    /**
     * Mark recipient as failed
     */
    @Transactional
    public void markRecipientFailed(TelegramCampaignRecipient recipient, String errorMessage) {
        recipient.setStatus(MessageStatus.FAILED);
        recipient.setErrorMessage(errorMessage);
        recipientRepository.save(recipient);
    }

    /**
     * Log message to telegram_logs
     */
    @Transactional
    public void logMessage(TelegramCampaign campaign, TelegramCampaignRecipient recipient,
                           String message, Integer telegramMessageId, MessageStatus status, String errorMessage) {
        try {
            TelegramLog logEntry = TelegramLog.builder()
                    .subscriber(recipient.getSubscriber())
                    .telegramUserId(recipient.getTelegramUserId())
                    .username(recipient.getSubscriber() != null ? recipient.getSubscriber().getUsername() : null)
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
            log.error("Failed to log message: {}", e.getMessage());
        }
    }

    /**
     * Send a single message (for automation or manual sending)
     */
    public boolean sendSingleMessage(Long chatId, String message, String imageUrl,
                                     List<List<Map<String, String>>> buttons) {
        Integer messageId;

        if (imageUrl != null && !imageUrl.isEmpty()) {
            if (buttons != null && !buttons.isEmpty()) {
                messageId = botService.sendPhotoWithButtons(chatId, imageUrl, message, buttons);
            } else {
                messageId = botService.sendPhoto(chatId, imageUrl, message);
            }
        } else {
            if (buttons != null && !buttons.isEmpty()) {
                messageId = botService.sendMessageWithButtons(chatId, message, buttons);
            } else {
                messageId = botService.sendMessage(chatId, message);
            }
        }

        return messageId != null;
    }

    /**
     * Send a template-based message to a subscriber
     */
    public boolean sendTemplateMessage(TelegramSubscriber subscriber, TelegramTemplate template,
                                       Map<String, String> placeholders) {
        if (subscriber == null || template == null) {
            return false;
        }

        String message = template.render(placeholders);
        List<List<Map<String, String>>> buttons = null;

        if (template.getHasButtons() && template.getButtonsConfig() != null) {
            try {
                buttons = objectMapper.readValue(template.getButtonsConfig(),
                        new TypeReference<List<List<Map<String, String>>>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse template buttons: {}", e.getMessage());
            }
        }

        String imageUrl = template.getHasImage() ? template.getImageUrl() : null;

        boolean success = sendSingleMessage(subscriber.getTelegramUserId(), message, imageUrl, buttons);

        // Log the message
        if (success) {
            logTemplateMessage(subscriber, template, message, MessageStatus.SENT, null);
        } else {
            logTemplateMessage(subscriber, template, message, MessageStatus.FAILED, "Failed to send");
        }

        // Update template usage count
        if (success) {
            template.setUsageCount(template.getUsageCount() + 1);
            templateRepository.save(template);
        }

        return success;
    }

    private void logTemplateMessage(TelegramSubscriber subscriber, TelegramTemplate template,
                                    String message, MessageStatus status, String error) {
        try {
            TelegramLog logEntry = TelegramLog.builder()
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
