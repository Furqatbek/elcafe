package com.elcafe.modules.loyalty.scheduler;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.entity.LoyaltyConfig;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.loyalty.repository.LoyaltyConfigRepository;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Opt-in loyalty automation. Two daily jobs, each OFF by default (enabling them moves customer bonus
 * balances — a product decision, not an ops one):
 *   - birthday bonus: grant the configured birthday bonus to every customer whose birthday is today.
 *   - bonus expiry: lapse the outstanding balance of customers with no bonus activity for the global
 *     config's {@code bonusExpiryDays} (rolling-inactivity expiry).
 *
 * Each customer is handled in its own transaction (through {@link LoyaltyService}) so one failure does
 * not abort the batch, and both operations are idempotent per customer/day, so re-runs and a
 * double-fire under {@code @SchedulerLock} are safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyBonusScheduler {

    private final CustomerRepository customerRepository;
    private final CustomerLoyaltyRepository customerLoyaltyRepository;
    private final LoyaltyConfigRepository loyaltyConfigRepository;
    private final LoyaltyService loyaltyService;

    @Value("${app.loyalty.birthday-bonus.enabled:false}")
    private boolean birthdayBonusEnabled;

    @Value("${app.loyalty.bonus-expiry.enabled:false}")
    private boolean bonusExpiryEnabled;

    /**
     * Grant the birthday bonus to every customer whose birthday is today. {@code grantBirthdayBonus}
     * is idempotent per customer/year, so re-runs and duplicate fires are safe.
     */
    @Scheduled(cron = "${app.loyalty.birthday-bonus.cron:0 0 8 * * ?}")
    @SchedulerLock(name = "loyalty-birthday-bonus", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    public void grantBirthdayBonuses() {
        if (!birthdayBonusEnabled) {
            return;
        }
        LocalDate today = LocalDate.now();
        List<Customer> customers = customerRepository.findByBirthDateMonthAndDay(
                today.getMonthValue(), today.getDayOfMonth());
        log.info("Loyalty birthday-bonus job: {} customer(s) with a birthday today", customers.size());

        int granted = 0;
        for (Customer customer : customers) {
            try {
                loyaltyService.grantBirthdayBonus(customer.getId());
                granted++;
            } catch (Exception e) {
                log.warn("Loyalty birthday bonus failed for customer {}: {}", customer.getId(), e.getMessage());
            }
        }
        log.info("Loyalty birthday-bonus job complete: {}/{} processed", granted, customers.size());
    }

    /**
     * Lapse the outstanding balance of customers inactive for the global config's
     * {@code bonusExpiryDays}. No-op if no global config sets a positive expiry window.
     */
    @Scheduled(cron = "${app.loyalty.bonus-expiry.cron:0 0 3 * * ?}")
    @SchedulerLock(name = "loyalty-bonus-expiry", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    public void expireStaleBonuses() {
        if (!bonusExpiryEnabled) {
            return;
        }
        Integer expiryDays = loyaltyConfigRepository.findGlobalConfig()
                .map(LoyaltyConfig::getBonusExpiryDays)
                .orElse(null);
        if (expiryDays == null || expiryDays <= 0) {
            log.debug("Loyalty bonus-expiry job: no global bonusExpiryDays configured — nothing to do");
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(expiryDays);
        List<Long> staleLoyaltyIds = customerLoyaltyRepository.findIdsWithBalanceAndNoActivitySince(cutoff);
        log.info("Loyalty bonus-expiry job: {} inactive customer(s) with a balance older than {} day(s)",
                staleLoyaltyIds.size(), expiryDays);

        int expired = 0;
        for (Long loyaltyId : staleLoyaltyIds) {
            try {
                if (loyaltyService.expireStaleBalance(loyaltyId, expiryDays)) {
                    expired++;
                }
            } catch (Exception e) {
                log.warn("Loyalty bonus expiry failed for loyalty {}: {}", loyaltyId, e.getMessage());
            }
        }
        log.info("Loyalty bonus-expiry job complete: expired {} balance(s)", expired);
    }
}
