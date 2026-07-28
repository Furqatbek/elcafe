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
        // Per restaurant, using that restaurant's own expiry window.
        //
        // This used to read bonusExpiryDays from the GLOBAL config alone, which made the job
        // unreachable in practice: LoyaltyConfig is @Filter-scoped and tenant enforcement is on by
        // default, so no restaurant could ever save a global row — the settings page reported success
        // and wrote an orphan nobody could read. A job whose only input the product cannot produce
        // never runs, which is the quietest way for balances to simply never expire.
        //
        // Reading each restaurant's own window is also the only correct behaviour: one shared number
        // would expire a 90-day restaurant's balances on a 30-day restaurant's schedule.
        List<LoyaltyConfig> configs = loyaltyConfigRepository.findAllEnabledPerRestaurantConfigs();
        if (configs.isEmpty()) {
            log.debug("Loyalty bonus-expiry job: no restaurant has loyalty enabled — nothing to do");
            return;
        }

        int expired = 0;
        int restaurantsSwept = 0;
        for (LoyaltyConfig config : configs) {
            Integer expiryDays = config.getBonusExpiryDays();
            if (expiryDays == null || expiryDays <= 0 || config.getRestaurant() == null) {
                continue;   // this restaurant does not expire balances
            }
            Long restaurantId = config.getRestaurant().getId();
            restaurantsSwept++;

            LocalDateTime cutoff = LocalDateTime.now().minusDays(expiryDays);
            List<Long> staleLoyaltyIds = customerLoyaltyRepository
                    .findIdsWithBalanceAndNoActivitySinceForRestaurant(restaurantId, cutoff);
            log.info("Loyalty bonus-expiry: restaurant {} — {} inactive customer(s) with a balance "
                    + "older than {} day(s)", restaurantId, staleLoyaltyIds.size(), expiryDays);

            for (Long loyaltyId : staleLoyaltyIds) {
                try {
                    if (loyaltyService.expireStaleBalance(loyaltyId, expiryDays)) {
                        expired++;
                    }
                } catch (Exception e) {
                    // One restaurant's bad row must not stop the sweep for everyone else.
                    log.warn("Loyalty bonus expiry failed for loyalty {} (restaurant {}): {}",
                            loyaltyId, restaurantId, e.getMessage());
                }
            }
        }
        log.info("Loyalty bonus-expiry job complete: expired {} balance(s) across {} restaurant(s)",
                expired, restaurantsSwept);
    }
}
