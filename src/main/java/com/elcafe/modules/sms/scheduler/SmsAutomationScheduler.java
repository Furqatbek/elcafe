package com.elcafe.modules.sms.scheduler;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.sms.entity.SmsAutomationRule;
import com.elcafe.modules.sms.enums.AutomationTrigger;
import com.elcafe.modules.sms.repository.SmsAutomationRuleRepository;
import com.elcafe.modules.sms.service.SmsAutomationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Scheduler for SMS automation tasks:
 * - Birthday greetings
 * - Inactive customer re-engagement
 * - Delayed automation messages
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsAutomationScheduler {

    private final SmsAutomationRuleRepository automationRuleRepository;
    private final SmsAutomationService automationService;
    private final CustomerRepository customerRepository;

    /**
     * Send birthday greetings to customers.
     * Runs daily at 9 AM.
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @SchedulerLock(name = "sms-birthday-greetings", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    @Transactional
    public void processBirthdayGreetings() {
        log.info("Starting birthday greetings scheduler");

        // Get active birthday automation rule
        List<SmsAutomationRule> birthdayRules = automationRuleRepository
                .findByTriggerTypeAndIsActiveTrue(AutomationTrigger.BIRTHDAY);

        if (birthdayRules.isEmpty()) {
            log.debug("No active birthday automation rules found");
            return;
        }

        // Get today's date
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int day = today.getDayOfMonth();

        // Find customers with birthday today
        List<Customer> birthdayCustomers = customerRepository.findByBirthDateMonthAndDay(month, day);

        log.info("Found {} customers with birthday today", birthdayCustomers.size());

        for (Customer customer : birthdayCustomers) {
            try {
                automationService.triggerBirthdaySms(customer);
            } catch (Exception e) {
                log.error("Failed to send birthday SMS to customer {}: {}",
                        customer.getId(), e.getMessage());
            }
        }
    }

    /**
     * Check for inactive customers and send re-engagement messages.
     * Runs daily at 10 AM.
     */
    @Scheduled(cron = "0 0 10 * * ?")
    @SchedulerLock(name = "sms-inactive-winback", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    @Transactional
    public void processInactiveCustomers() {
        log.info("Starting inactive customer re-engagement scheduler");

        // Get active inactive customer rules
        List<SmsAutomationRule> inactiveRules = automationRuleRepository
                .findByTriggerTypeAndIsActiveTrue(AutomationTrigger.INACTIVE_CUSTOMER);

        if (inactiveRules.isEmpty()) {
            log.debug("No active inactive customer automation rules found");
            return;
        }

        for (SmsAutomationRule rule : inactiveRules) {
            try {
                // Get inactivity threshold from rule conditions or default to 30 days
                int inactiveDays = getInactiveDaysFromRule(rule);
                OffsetDateTime inactiveSince = OffsetDateTime.now(ZoneOffset.UTC).minusDays(inactiveDays);

                // Find inactive customers
                List<Customer> inactiveCustomers = customerRepository
                        .findInactiveCustomers(inactiveSince);

                log.info("Found {} customers inactive for {} days",
                        inactiveCustomers.size(), inactiveDays);

                for (Customer customer : inactiveCustomers) {
                    try {
                        automationService.triggerAutomation(
                                AutomationTrigger.INACTIVE_CUSTOMER,
                                customer,
                                null
                        );
                    } catch (Exception e) {
                        log.error("Failed to send re-engagement SMS to customer {}: {}",
                                customer.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing inactive customer rule {}: {}",
                        rule.getId(), e.getMessage());
            }
        }
    }

    /**
     * Process review request messages for recent orders.
     * Runs daily at 11 AM - sends review requests 3 days after delivery.
     */
    @Scheduled(cron = "0 0 11 * * ?")
    @SchedulerLock(name = "sms-review-requests", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    @Transactional
    public void processReviewRequests() {
        log.info("Starting review request scheduler");

        List<SmsAutomationRule> reviewRules = automationRuleRepository
                .findByTriggerTypeAndIsActiveTrue(AutomationTrigger.REVIEW_REQUEST);

        if (reviewRules.isEmpty()) {
            log.debug("No active review request automation rules found");
            return;
        }

        // This would need integration with order service to find
        // orders delivered X days ago that haven't received a review request
        log.info("Review request automation is configured. " +
                "Integration with order delivery tracking required.");
    }

    /**
     * Extract inactivity days threshold from rule conditions.
     */
    private int getInactiveDaysFromRule(SmsAutomationRule rule) {
        if (rule.getConditions() != null && rule.getConditions().containsKey("inactiveDays")) {
            try {
                return Integer.parseInt(rule.getConditions().get("inactiveDays").toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid inactiveDays in rule {}, using default", rule.getId());
            }
        }
        return 30; // Default 30 days
    }
}
