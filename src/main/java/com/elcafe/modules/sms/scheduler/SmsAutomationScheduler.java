package com.elcafe.modules.sms.scheduler;

import com.elcafe.common.tenant.TenantContext;
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
import java.util.Objects;

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

        // V165: rules are per-restaurant now, so run one pass per restaurant that has a birthday
        // rule. Binding TenantContext makes the customer lookup below resolve through the §3.4
        // restaurantFilter — this scheduler thread has no request, so without it the query would
        // return EVERY tenant's birthday customers and text them all from one rule.
        for (Long restaurantId : birthdayRules.stream()
                .map(SmsAutomationRule::getRestaurantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList()) {
            TenantContext.setRestaurantId(restaurantId);
            try {
                List<Customer> birthdayCustomers =
                        customerRepository.findByBirthDateMonthAndDay(month, day);
                log.info("Restaurant {}: {} customer(s) with a birthday today",
                        restaurantId, birthdayCustomers.size());

                for (Customer customer : birthdayCustomers) {
                    try {
                        automationService.triggerBirthdaySms(customer);
                    } catch (Exception e) {
                        log.error("Failed to send birthday SMS to customer {}: {}",
                                customer.getId(), e.getMessage());
                    }
                }
            } finally {
                TenantContext.clear();
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
            // V165: each rule belongs to one restaurant and may only reach that restaurant's
            // customers. TenantContext is what scopes findInactiveCustomers on this scheduler
            // thread (see processBirthdayGreetings).
            TenantContext.setRestaurantId(rule.getRestaurantId());
            try {
                // Get inactivity threshold from rule conditions or default to 30 days
                int inactiveDays = getInactiveDaysFromRule(rule);
                OffsetDateTime inactiveSince = OffsetDateTime.now(ZoneOffset.UTC).minusDays(inactiveDays);

                // Find inactive customers
                List<Customer> inactiveCustomers = customerRepository
                        .findInactiveCustomers(inactiveSince);

                log.info("Restaurant {}: {} customer(s) inactive for {} days",
                        rule.getRestaurantId(), inactiveCustomers.size(), inactiveDays);

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
            } finally {
                TenantContext.clear();
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
