package com.elcafe.modules.telegram.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.enums.MessageStatus;
import com.elcafe.modules.telegram.dto.TelegramCampaignRequest;
import com.elcafe.modules.telegram.dto.TelegramCampaignResponse;
import com.elcafe.modules.telegram.entity.*;
import com.elcafe.modules.telegram.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramCampaignService {

    private final TelegramCampaignRepository campaignRepository;
    private final TelegramCampaignRecipientRepository recipientRepository;
    private final TelegramTemplateRepository templateRepository;
    private final TelegramSubscriberRepository subscriberRepository;
    private final TelegramLogRepository logRepository;

    @Transactional(readOnly = true)
    public Page<TelegramCampaignResponse> getAllCampaigns(Pageable pageable) {
        return campaignRepository.findAll(pageable).map(TelegramCampaignResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TelegramCampaignResponse> getCampaignsByStatus(CampaignStatus status, Pageable pageable) {
        return campaignRepository.findByStatus(status, pageable).map(TelegramCampaignResponse::from);
    }

    @Transactional(readOnly = true)
    public TelegramCampaignResponse getCampaignById(Long id) {
        TelegramCampaign campaign = campaignRepository.findByIdWithTemplate(id);
        if (campaign == null) {
            throw new ResourceNotFoundException("TelegramCampaign", "id", id);
        }
        return TelegramCampaignResponse.from(campaign);
    }

    @Transactional
    public TelegramCampaignResponse createCampaign(TelegramCampaignRequest request) {
        log.info("Creating Telegram campaign: {}", request.getName());

        TelegramCampaign campaign = TelegramCampaign.builder()
                .name(request.getName())
                .description(request.getDescription())
                .customMessage(request.getCustomMessage())
                .imageUrl(request.getImageUrl())
                .buttonsConfig(request.getButtonsConfig())
                .targetAudience(request.getTargetAudience())
                .filterCriteria(request.getFilterCriteria())
                .status(CampaignStatus.DRAFT)
                .build();

        if (request.getTemplateId() != null) {
            TelegramTemplate template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException("TelegramTemplate", "id", request.getTemplateId()));
            campaign.setTemplate(template);
        }

        if (request.getScheduledAt() != null) {
            campaign.setScheduledAt(request.getScheduledAt());
        }

        campaign = campaignRepository.save(campaign);

        // Build recipient list
        List<TelegramSubscriber> recipients = buildRecipientList(campaign);
        campaign.setRecipientCount(recipients.size());
        campaign = campaignRepository.save(campaign);

        // Create recipient records
        createRecipientRecords(campaign, recipients);

        log.info("Telegram campaign created with ID: {}, recipients: {}", campaign.getId(), recipients.size());
        return TelegramCampaignResponse.from(campaign);
    }

    @Transactional
    public TelegramCampaignResponse updateCampaign(Long id, TelegramCampaignRequest request) {
        log.info("Updating Telegram campaign: {}", id);

        TelegramCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramCampaign", "id", id));

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new BadRequestException("Can only update campaigns in DRAFT status");
        }

        campaign.setName(request.getName());
        campaign.setDescription(request.getDescription());
        campaign.setCustomMessage(request.getCustomMessage());
        campaign.setImageUrl(request.getImageUrl());
        campaign.setButtonsConfig(request.getButtonsConfig());
        campaign.setTargetAudience(request.getTargetAudience());
        campaign.setFilterCriteria(request.getFilterCriteria());
        campaign.setScheduledAt(request.getScheduledAt());

        if (request.getTemplateId() != null) {
            TelegramTemplate template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException("TelegramTemplate", "id", request.getTemplateId()));
            campaign.setTemplate(template);
        } else {
            campaign.setTemplate(null);
        }

        campaign = campaignRepository.save(campaign);
        log.info("Telegram campaign updated: {}", id);
        return TelegramCampaignResponse.from(campaign);
    }

    @Transactional
    public TelegramCampaignResponse sendCampaignNow(Long id) {
        log.info("Starting Telegram campaign: {}", id);

        TelegramCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramCampaign", "id", id));

        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new BadRequestException("Can only send campaigns in DRAFT or SCHEDULED status");
        }

        campaign.setStatus(CampaignStatus.SENDING);
        campaign.setStartedAt(LocalDateTime.now());
        campaign = campaignRepository.save(campaign);

        // Note: Actual sending would be done via Telegram Bot API
        // This is a placeholder for the sending logic
        log.info("Campaign {} marked as SENDING. Actual Telegram sending would be triggered here.", id);

        return TelegramCampaignResponse.from(campaign);
    }

    @Transactional
    public TelegramCampaignResponse cancelCampaign(Long id) {
        log.info("Cancelling Telegram campaign: {}", id);

        TelegramCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramCampaign", "id", id));

        if (campaign.getStatus() == CampaignStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel completed campaign");
        }

        campaign.setStatus(CampaignStatus.CANCELLED);
        campaign = campaignRepository.save(campaign);

        log.info("Telegram campaign cancelled: {}", id);
        return TelegramCampaignResponse.from(campaign);
    }

    @Transactional
    public void deleteCampaign(Long id) {
        log.info("Deleting Telegram campaign: {}", id);
        TelegramCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramCampaign", "id", id));

        if (campaign.getStatus() == CampaignStatus.SENDING) {
            throw new BadRequestException("Cannot delete campaign that is currently sending");
        }

        campaignRepository.delete(campaign);
        log.info("Telegram campaign deleted: {}", id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignStats(Long campaignId) {
        TelegramCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramCampaign", "id", campaignId));

        List<Object[]> statusCounts = recipientRepository.getStatusCountsByCampaign(campaignId);
        Map<String, Long> statusMap = new HashMap<>();
        for (Object[] row : statusCounts) {
            statusMap.put(((MessageStatus) row[0]).name(), (Long) row[1]);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("campaignId", campaignId);
        stats.put("campaignName", campaign.getName());
        stats.put("status", campaign.getStatus());
        stats.put("recipientCount", campaign.getRecipientCount());
        stats.put("sentCount", campaign.getSentCount());
        stats.put("deliveredCount", campaign.getDeliveredCount());
        stats.put("failedCount", campaign.getFailedCount());
        stats.put("deliveryRate", campaign.getDeliveryRate());
        stats.put("statusBreakdown", statusMap);
        stats.put("startedAt", campaign.getStartedAt());
        stats.put("completedAt", campaign.getCompletedAt());

        return stats;
    }

    // ========== Helper Methods ==========

    private List<TelegramSubscriber> buildRecipientList(TelegramCampaign campaign) {
        switch (campaign.getTargetAudience()) {
            case ALL:
                return subscriberRepository.findByIsActiveTrueAndIsBlockedFalse();
            case ACTIVE:
                int activeDays = 7;
                if (campaign.getFilterCriteria() != null && campaign.getFilterCriteria().containsKey("active_days")) {
                    activeDays = (Integer) campaign.getFilterCriteria().get("active_days");
                }
                LocalDateTime activeSince = LocalDateTime.now().minusDays(activeDays);
                return subscriberRepository.findActiveSubscribers(activeSince);
            case INACTIVE:
                int inactiveDays = 14;
                if (campaign.getFilterCriteria() != null && campaign.getFilterCriteria().containsKey("days_inactive")) {
                    inactiveDays = (Integer) campaign.getFilterCriteria().get("days_inactive");
                }
                LocalDateTime inactiveBefore = LocalDateTime.now().minusDays(inactiveDays);
                return subscriberRepository.findInactiveSubscribers(inactiveBefore);
            case LINKED_CUSTOMERS:
                return subscriberRepository.findByCustomerIdIsNotNull();
            case CUSTOM:
                return new ArrayList<>();
            default:
                return subscriberRepository.findByIsActiveTrueAndIsBlockedFalse();
        }
    }

    private void createRecipientRecords(TelegramCampaign campaign, List<TelegramSubscriber> subscribers) {
        List<TelegramCampaignRecipient> recipients = subscribers.stream()
                .map(s -> TelegramCampaignRecipient.builder()
                        .campaign(campaign)
                        .subscriber(s)
                        .telegramUserId(s.getTelegramUserId())
                        .status(MessageStatus.PENDING)
                        .build())
                .toList();

        recipientRepository.saveAll(recipients);
    }
}
