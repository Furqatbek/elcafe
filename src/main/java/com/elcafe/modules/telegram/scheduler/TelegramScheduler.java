package com.elcafe.modules.telegram.scheduler;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.telegram.entity.TelegramAutomationRule;
import com.elcafe.modules.telegram.entity.TelegramCampaign;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.entity.TelegramTemplate;
import com.elcafe.modules.telegram.enums.TelegramTriggerType;
import com.elcafe.modules.telegram.repository.TelegramAutomationRuleRepository;
import com.elcafe.modules.telegram.repository.TelegramCampaignRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
import com.elcafe.modules.telegram.repository.TelegramTemplateRepository;
import com.elcafe.modules.telegram.service.TelegramCampaignExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduler for Telegram marketing campaigns and automation rules.
 * Runs periodically to:
 * - Start scheduled campaigns
 * - Trigger automation rules (birthday, inactive users, etc.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramScheduler {

    private final TelegramCampaignRepository campaignRepository;
    private final TelegramCampaignExecutor campaignExecutor;
    private final TelegramAutomationRuleRepository automationRuleRepository;
    private final TelegramSubscriberRepository subscriberRepository;
    private final TelegramTemplateRepository templateRepository;
    private final CustomerRepository customerRepository;
    private final ShiftTimeService shiftTimeService;
    private final RestaurantRepository restaurantRepository;

    /**
     * Check for scheduled campaigns every minute.
     * If a campaign's scheduledAt time has passed, start sending.
     */
    @Scheduled(fixedRate = 60000) // Every minute
    @Transactional
    public void processScheduledCampaigns() {
        try {
            List<TelegramCampaign> scheduledCampaigns = campaignRepository
                    .findByStatusAndScheduledAtBefore(CampaignStatus.SCHEDULED, OffsetDateTime.now(ZoneOffset.UTC));

            for (TelegramCampaign campaign : scheduledCampaigns) {
                log.info("Starting scheduled campaign: id={}, name={}", campaign.getId(), campaign.getName());

                campaign.setStatus(CampaignStatus.SENDING);
                campaign.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
                campaignRepository.save(campaign);

                // Execute campaign asynchronously
                campaignExecutor.executeCampaign(campaign.getId());
            }

            if (!scheduledCampaigns.isEmpty()) {
                log.info("Started {} scheduled campaigns", scheduledCampaigns.size());
            }
        } catch (Exception e) {
            log.error("Error processing scheduled campaigns: {}", e.getMessage());
        }
    }

    /**
     * Process birthday automation every day at 9:00 AM.
     * Sends birthday messages to subscribers whose linked customers have birthday today.
     */
    @Scheduled(cron = "0 0 9 * * ?") // Every day at 9:00 AM
    @Transactional
    public void processBirthdayAutomation() {
        try {
            log.info("Processing birthday automation...");

            // Find active birthday automation rule
            List<TelegramAutomationRule> rules = automationRuleRepository
                    .findByTriggerTypeAndIsActiveTrue(TelegramTriggerType.BIRTHDAY);

            if (rules.isEmpty()) {
                log.debug("No active birthday automation rules found");
                return;
            }

            LocalDate today = LocalDate.now();
            int month = today.getMonthValue();
            int day = today.getDayOfMonth();

            // Find customers with birthday today
            List<Customer> birthdayCustomers = customerRepository.findByBirthDateMonthAndDay(month, day);
            log.info("Found {} customers with birthday today", birthdayCustomers.size());

            for (Customer customer : birthdayCustomers) {
                // Find subscriber linked to this customer
                TelegramSubscriber subscriber = subscriberRepository.findByCustomerId(customer.getId())
                        .orElse(null);

                if (subscriber == null || subscriber.getIsBlocked()) {
                    continue;
                }

                for (TelegramAutomationRule rule : rules) {
                    if (rule.getTemplate() == null) continue;

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("name", customer.getFirstName());
                    placeholders.put("first_name", customer.getFirstName());
                    placeholders.put("last_name", customer.getLastName() != null ? customer.getLastName() : "");

                    boolean success = campaignExecutor.sendTemplateMessage(subscriber, rule.getTemplate(), placeholders);

                    if (success) {
                        rule.incrementSentCount();
                        rule.setLastTriggeredAt(OffsetDateTime.now(ZoneOffset.UTC));
                        automationRuleRepository.save(rule);
                        log.info("Sent birthday message to subscriber: {}", subscriber.getTelegramUserId());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error processing birthday automation: {}", e.getMessage());
        }
    }

    /**
     * Process inactive user automation every day at 10:00 AM.
     * Sends win-back messages to subscribers who haven't interacted recently.
     */
    @Scheduled(cron = "0 0 10 * * ?") // Every day at 10:00 AM
    @Transactional
    public void processInactiveUserAutomation() {
        try {
            log.info("Processing inactive user automation...");

            // Find active inactive user automation rules
            List<TelegramAutomationRule> rules = automationRuleRepository
                    .findByTriggerTypeAndIsActiveTrue(TelegramTriggerType.INACTIVE_USER);

            if (rules.isEmpty()) {
                log.debug("No active inactive user automation rules found");
                return;
            }

            // Get primary restaurant for shift-aware calculations
            Long restaurantId = getPrimaryRestaurantId();
            LocalDate currentBusinessDay = shiftTimeService.getCurrentBusinessDay(restaurantId);

            for (TelegramAutomationRule rule : rules) {
                if (rule.getTemplate() == null) continue;

                // Get days from conditions or default to 14
                int daysInactive = 14;
                if (rule.getConditions() != null && rule.getConditions().containsKey("days_inactive")) {
                    daysInactive = ((Number) rule.getConditions().get("days_inactive")).intValue();
                }

                // Use shift-aware date calculation
                LocalDate cutoffDate = currentBusinessDay.minusDays(daysInactive);
                ShiftTimeService.ShiftTimeRange shiftRange = shiftTimeService.getShiftTimeRange(
                        restaurantId, cutoffDate);
                List<TelegramSubscriber> inactiveSubscribers = subscriberRepository.findInactiveSubscribers(shiftRange.start());

                log.info("Found {} inactive subscribers ({}+ business days)", inactiveSubscribers.size(), daysInactive);

                int sentCount = 0;
                for (TelegramSubscriber subscriber : inactiveSubscribers) {
                    if (subscriber.getIsBlocked()) continue;

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("name", subscriber.getFirstName() != null ? subscriber.getFirstName() : "Hurmatli mijoz");

                    boolean success = campaignExecutor.sendTemplateMessage(subscriber, rule.getTemplate(), placeholders);

                    if (success) {
                        sentCount++;
                        // Rate limiting - wait 50ms between messages
                        Thread.sleep(50);
                    }
                }

                if (sentCount > 0) {
                    rule.setSentCount(rule.getSentCount() + sentCount);
                    rule.setLastTriggeredAt(OffsetDateTime.now(ZoneOffset.UTC));
                    automationRuleRepository.save(rule);
                    log.info("Sent {} inactive user messages for rule: {}", sentCount, rule.getName());
                }
            }

        } catch (Exception e) {
            log.error("Error processing inactive user automation: {}", e.getMessage());
        }
    }

    /**
     * Clean up stuck campaigns every hour.
     * Campaigns stuck in SENDING status for more than 2 hours are marked as failed.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void cleanupStuckCampaigns() {
        try {
            LocalDateTime twoHoursAgo = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);

            List<TelegramCampaign> stuckCampaigns = campaignRepository
                    .findByStatusAndStartedAtBefore(CampaignStatus.SENDING, twoHoursAgo);

            for (TelegramCampaign campaign : stuckCampaigns) {
                log.warn("Marking stuck campaign as cancelled: id={}, name={}", campaign.getId(), campaign.getName());
                campaign.setStatus(CampaignStatus.CANCELLED);
                campaign.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
                campaignRepository.save(campaign);
            }

            if (!stuckCampaigns.isEmpty()) {
                log.info("Cleaned up {} stuck campaigns", stuckCampaigns.size());
            }
        } catch (Exception e) {
            log.error("Error cleaning up stuck campaigns: {}", e.getMessage());
        }
    }

    /**
     * Get the primary restaurant ID for shift-aware calculations.
     * Uses the first active restaurant's business hours.
     * Returns null if no active restaurants exist (will use calendar dates as fallback).
     */
    private Long getPrimaryRestaurantId() {
        List<Restaurant> activeRestaurants = restaurantRepository.findByActiveTrue();
        if (activeRestaurants.isEmpty()) {
            log.debug("No active restaurants found, using calendar dates for automation");
            return null;
        }
        return activeRestaurants.get(0).getId();
    }
}
