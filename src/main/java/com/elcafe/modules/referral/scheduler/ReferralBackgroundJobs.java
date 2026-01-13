package com.elcafe.modules.referral.scheduler;

import com.elcafe.modules.referral.service.ReferralService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background jobs for referral program management.
 * Handles expiration of pending referrals and cleanup tasks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferralBackgroundJobs {

    private final ReferralService referralService;

    private static final int DEFAULT_EXPIRY_DAYS = 30;

    /**
     * Expire pending referrals that haven't been completed within the expiry period.
     * Runs daily at 2 AM.
     *
     * Business Rule: Pending referrals older than the configured expiry period
     * (default 30 days) are automatically expired.
     */
    @Scheduled(cron = "0 0 2 * * *") // Every day at 2 AM
    public void expirePendingReferrals() {
        try {
            log.info("Starting referral expiration job");
            referralService.expirePendingReferrals(DEFAULT_EXPIRY_DAYS);
            log.info("Referral expiration job completed");
        } catch (Exception e) {
            log.error("Referral expiration job failed: {}", e.getMessage(), e);
        }
    }
}
