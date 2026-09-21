package com.elcafe.modules.sms.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.sms.dto.*;
import com.elcafe.modules.sms.entity.*;
import com.elcafe.modules.sms.enums.*;
import com.elcafe.modules.sms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCampaignService {

    private final SmsCampaignRepository campaignRepository;
    private final SmsCampaignRecipientRepository recipientRepository;
    private final SmsTemplateRepository templateRepository;
    private final SmsLogRepository logRepository;
    private final CustomerRepository customerRepository;
    private final SmsService smsService;

    @Transactional(readOnly = true)
    public Page<SmsCampaignResponse> getAllCampaigns(Pageable pageable) {
        return campaignRepository.findAll(pageable).map(SmsCampaignResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<SmsCampaignResponse> getCampaignsByStatus(CampaignStatus status, Pageable pageable) {
        return campaignRepository.findByStatus(status, pageable).map(SmsCampaignResponse::from);
    }

    @Transactional(readOnly = true)
    public SmsCampaignResponse getCampaignById(Long id) {
        SmsCampaign campaign = campaignRepository.findByIdWithTemplate(id);
        if (campaign == null) {
            throw new ResourceNotFoundException("SmsCampaign", "id", id);
        }
        return SmsCampaignResponse.from(campaign);
    }

    @Transactional
    public SmsCampaignResponse createCampaign(SmsCampaignRequest request) {
        log.info("Creating SMS campaign: {}", request.getName());

        SmsCampaign campaign = SmsCampaign.builder()
                .name(request.getName())
                .description(request.getDescription())
                .customMessage(request.getCustomMessage())
                .targetAudience(request.getTargetAudience())
                .segmentId(request.getSegmentId())
                .filterCriteria(request.getFilterCriteria())
                .status(CampaignStatus.DRAFT)
                .build();

        if (request.getTemplateId() != null) {
            SmsTemplate template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException("SmsTemplate", "id", request.getTemplateId()));
            campaign.setTemplate(template);
        }

        if (request.getScheduledAt() != null) {
            campaign.setScheduledAt(request.getScheduledAt());
        }

        campaign = campaignRepository.save(campaign);

        // Build recipient list
        List<Customer> recipients = buildRecipientList(campaign);
        campaign.setRecipientCount(recipients.size());
        campaign = campaignRepository.save(campaign);

        // Create recipient records
        createRecipientRecords(campaign, recipients);

        log.info("SMS campaign created with ID: {}, recipients: {}", campaign.getId(), recipients.size());
        return SmsCampaignResponse.from(campaign);
    }

    @Transactional
    public SmsCampaignResponse updateCampaign(Long id, SmsCampaignRequest request) {
        log.info("Updating SMS campaign: {}", id);

        SmsCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsCampaign", "id", id));

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new BadRequestException("Can only update campaigns in DRAFT status");
        }

        campaign.setName(request.getName());
        campaign.setDescription(request.getDescription());
        campaign.setCustomMessage(request.getCustomMessage());
        campaign.setTargetAudience(request.getTargetAudience());
        campaign.setSegmentId(request.getSegmentId());
        campaign.setFilterCriteria(request.getFilterCriteria());
        campaign.setScheduledAt(request.getScheduledAt());

        if (request.getTemplateId() != null) {
            SmsTemplate template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException("SmsTemplate", "id", request.getTemplateId()));
            campaign.setTemplate(template);
        } else {
            campaign.setTemplate(null);
        }

        campaign = campaignRepository.save(campaign);
        log.info("SMS campaign updated: {}", id);
        return SmsCampaignResponse.from(campaign);
    }

    @Transactional
    public SmsCampaignResponse scheduleCampaign(Long id, OffsetDateTime scheduledAt) {
        log.info("Scheduling SMS campaign: {} for {}", id, scheduledAt);

        SmsCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsCampaign", "id", id));

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new BadRequestException("Can only schedule campaigns in DRAFT status");
        }

        if (scheduledAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BadRequestException("Scheduled time must be in the future");
        }

        campaign.setScheduledAt(scheduledAt);
        campaign.setStatus(CampaignStatus.SCHEDULED);
        campaign = campaignRepository.save(campaign);

        log.info("SMS campaign scheduled: {}", id);
        return SmsCampaignResponse.from(campaign);
    }

    @Transactional
    public SmsCampaignResponse sendCampaignNow(Long id) {
        log.info("Sending SMS campaign immediately: {}", id);

        SmsCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsCampaign", "id", id));

        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new BadRequestException("Can only send campaigns in DRAFT or SCHEDULED status");
        }

        campaign.setStatus(CampaignStatus.SENDING);
        campaign.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
        campaign = campaignRepository.save(campaign);

        // Send asynchronously
        sendCampaignAsync(campaign.getId());

        return SmsCampaignResponse.from(campaign);
    }

    @Async
    @Transactional
    public void sendCampaignAsync(Long campaignId) {
        log.info("Starting async campaign sending: {}", campaignId);

        SmsCampaign campaign = campaignRepository.findByIdWithTemplate(campaignId);
        if (campaign == null) {
            log.error("Campaign not found: {}", campaignId);
            return;
        }

        List<SmsCampaignRecipient> recipients = recipientRepository.findPendingByCampaignId(campaignId);
        String messageTemplate = campaign.getMessage();

        for (SmsCampaignRecipient recipient : recipients) {
            try {
                // Personalize message
                String personalizedMessage = personalizeMessage(messageTemplate, recipient);
                recipient.setMessageContent(personalizedMessage);

                // Send SMS via Eskiz
                SendSmsResponse response = smsService.sendSms(
                        SendSmsRequest.builder()
                                .mobilePhone(recipient.getPhone())
                                .message(personalizedMessage)
                                .build()
                );

                if (response != null && response.getData() != null) {
                    recipient.markAsSent(response.getData().getId());
                    campaign.incrementSentCount();

                    // Log the message
                    createSmsLog(recipient, campaign, SmsMessageType.CAMPAIGN);
                } else {
                    recipient.markAsFailed("No response from SMS service");
                    campaign.incrementFailedCount();
                }

            } catch (Exception e) {
                log.error("Failed to send SMS to {}: {}", recipient.getPhone(), e.getMessage());
                recipient.markAsFailed(e.getMessage());
                campaign.incrementFailedCount();
            }

            recipientRepository.save(recipient);
        }

        // Mark campaign as completed
        campaign.setStatus(CampaignStatus.COMPLETED);
        campaign.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        campaignRepository.save(campaign);

        log.info("Campaign {} completed. Sent: {}, Failed: {}",
                campaignId, campaign.getSentCount(), campaign.getFailedCount());
    }

    @Transactional
    public SmsCampaignResponse cancelCampaign(Long id) {
        log.info("Cancelling SMS campaign: {}", id);

        SmsCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsCampaign", "id", id));

        if (campaign.getStatus() == CampaignStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel completed campaign");
        }

        campaign.setStatus(CampaignStatus.CANCELLED);
        campaign = campaignRepository.save(campaign);

        log.info("SMS campaign cancelled: {}", id);
        return SmsCampaignResponse.from(campaign);
    }

    @Transactional
    public void deleteCampaign(Long id) {
        log.info("Deleting SMS campaign: {}", id);
        SmsCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsCampaign", "id", id));

        if (campaign.getStatus() == CampaignStatus.SENDING) {
            throw new BadRequestException("Cannot delete campaign that is currently sending");
        }

        campaignRepository.delete(campaign);
        log.info("SMS campaign deleted: {}", id);
    }

    @Transactional(readOnly = true)
    public Page<SmsCampaignRecipientResponse> getCampaignRecipients(Long campaignId, Pageable pageable) {
        return recipientRepository.findByCampaignId(campaignId, pageable)
                .map(SmsCampaignRecipientResponse::from);
    }

    @Transactional(readOnly = true)
    public CampaignStatsResponse getCampaignStats(Long campaignId) {
        SmsCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("SmsCampaign", "id", campaignId));

        List<Object[]> statusCounts = recipientRepository.getStatusCountsByCampaign(campaignId);
        Map<String, Long> statusMap = new HashMap<>();
        for (Object[] row : statusCounts) {
            // row[0] is returned as String (enum name) from JPQL query
            String statusName = row[0] instanceof MessageStatus
                    ? ((MessageStatus) row[0]).name()
                    : row[0].toString();
            statusMap.put(statusName, (Long) row[1]);
        }

        return CampaignStatsResponse.builder()
                .campaignId(campaignId)
                .campaignName(campaign.getName())
                .status(campaign.getStatus())
                .recipientCount(campaign.getRecipientCount())
                .sentCount(campaign.getSentCount())
                .deliveredCount(campaign.getDeliveredCount())
                .failedCount(campaign.getFailedCount())
                .deliveryRate(campaign.getDeliveryRate())
                .totalCost(campaign.getTotalCost())
                .statusBreakdown(statusMap)
                .startedAt(campaign.getStartedAt())
                .completedAt(campaign.getCompletedAt())
                .build();
    }

    // ========== Helper Methods ==========

    private List<Customer> buildRecipientList(SmsCampaign campaign) {
        switch (campaign.getTargetAudience()) {
            case ALL:
                return customerRepository.findByActiveTrue();
            case BIRTHDAY_TODAY:
                LocalDate today = LocalDate.now();
                return customerRepository.findByBirthDateMonthAndDay(today.getMonthValue(), today.getDayOfMonth());
            case INACTIVE:
                int daysInactive = 30;
                if (campaign.getFilterCriteria() != null && campaign.getFilterCriteria().containsKey("days_inactive")) {
                    // JSON numbers may be Long, use Number to handle both Integer and Long
                    daysInactive = ((Number) campaign.getFilterCriteria().get("days_inactive")).intValue();
                }
                OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(daysInactive);
                return customerRepository.findInactiveCustomers(cutoff);
            case NEW_CUSTOMERS:
                int days = 7;
                if (campaign.getFilterCriteria() != null && campaign.getFilterCriteria().containsKey("days")) {
                    // JSON numbers may be Long, use Number to handle both Integer and Long
                    days = ((Number) campaign.getFilterCriteria().get("days")).intValue();
                }
                OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
                return customerRepository.findByCreatedAtAfter(since);
            case SEGMENT:
                // TODO: Implement segment-based targeting
                return new ArrayList<>();
            case CUSTOM:
                // Custom phone list handled separately
                return new ArrayList<>();
            default:
                return customerRepository.findByActiveTrue();
        }
    }

    private void createRecipientRecords(SmsCampaign campaign, List<Customer> customers) {
        List<SmsCampaignRecipient> recipients = customers.stream()
                .filter(c -> c.getPhone() != null && !c.getPhone().isEmpty())
                .map(c -> SmsCampaignRecipient.builder()
                        .campaign(campaign)
                        .customerId(c.getId())
                        .phone(c.getPhone())
                        .customerName(c.getFirstName() + " " + c.getLastName())
                        .status(MessageStatus.PENDING)
                        .build())
                .collect(Collectors.toList());

        recipientRepository.saveAll(recipients);
    }

    private String personalizeMessage(String template, SmsCampaignRecipient recipient) {
        if (template == null) return "";
        return template
                .replace("{name}", recipient.getCustomerName() != null ? recipient.getCustomerName() : "")
                .replace("{phone}", recipient.getPhone() != null ? recipient.getPhone() : "");
    }

    private void createSmsLog(SmsCampaignRecipient recipient, SmsCampaign campaign, SmsMessageType type) {
        SmsLog log = SmsLog.builder()
                .customerId(recipient.getCustomerId())
                .phone(recipient.getPhone())
                .customerName(recipient.getCustomerName())
                .message(recipient.getMessageContent())
                .messageType(type)
                .template(campaign.getTemplate())
                .campaign(campaign)
                .eskizMessageId(recipient.getEskizMessageId())
                .status(recipient.getStatus())
                .sentAt(recipient.getSentAt())
                .build();

        logRepository.save(log);
    }
}
