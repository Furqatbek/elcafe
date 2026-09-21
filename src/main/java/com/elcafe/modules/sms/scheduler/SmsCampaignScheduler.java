package com.elcafe.modules.sms.scheduler;

import com.elcafe.modules.sms.entity.SmsCampaign;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.repository.SmsCampaignRepository;
import com.elcafe.modules.sms.service.SmsCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Scheduler for automatic execution of scheduled SMS campaigns.
 * Runs every minute to check for campaigns that are due to be sent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsCampaignScheduler {

    private final SmsCampaignRepository campaignRepository;
    private final SmsCampaignService campaignService;

    /**
     * Check for scheduled campaigns that are due to be sent.
     * Runs every minute.
     */
    @Scheduled(fixedRate = 60000) // Every 60 seconds
    @Transactional
    public void processScheduledCampaigns() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<SmsCampaign> dueCampaigns = campaignRepository
                .findByStatusAndScheduledAtBefore(CampaignStatus.SCHEDULED, now);

        if (!dueCampaigns.isEmpty()) {
            log.info("Found {} scheduled SMS campaigns due for sending", dueCampaigns.size());
        }

        for (SmsCampaign campaign : dueCampaigns) {
            try {
                log.info("Auto-executing scheduled SMS campaign: {} (ID: {})",
                        campaign.getName(), campaign.getId());
                campaignService.sendCampaignNow(campaign.getId());
            } catch (Exception e) {
                log.error("Failed to execute scheduled campaign {}: {}",
                        campaign.getId(), e.getMessage(), e);
                // Mark campaign as failed if needed
                campaign.setStatus(CampaignStatus.CANCELLED);
                campaignRepository.save(campaign);
            }
        }
    }

    /**
     * Clean up old completed campaigns data (optional maintenance task).
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldCampaignData() {
        OffsetDateTime thresholdDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(90);

        // Log old campaigns that could be archived
        long oldCampaigns = campaignRepository.countByStatusAndCreatedAtBefore(
                CampaignStatus.COMPLETED, thresholdDate);

        if (oldCampaigns > 0) {
            log.info("Found {} completed SMS campaigns older than 90 days that could be archived",
                    oldCampaigns);
        }
    }
}
