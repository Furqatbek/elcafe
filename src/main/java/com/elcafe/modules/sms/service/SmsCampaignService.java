package com.elcafe.modules.sms.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.utils.LogSanitizer;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.dto.CustomerActivityDTO;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.customer.service.CustomerActivityService;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCampaignService {

    private final SmsCampaignRepository campaignRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;
    private final SmsCampaignRecipientRepository recipientRepository;
    private final SmsTemplateRepository templateRepository;
    private final SmsLogRepository logRepository;
    private final CustomerRepository customerRepository;
    private final CustomerActivityService customerActivityService;
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
        requireSupportedAudience(request.getTargetAudience());
        requireValidSegmentCriteria(request.getTargetAudience(), request.getFilterCriteria());
        requireValidCustomCriteria(request.getTargetAudience(), request.getFilterCriteria());

        SmsCampaign campaign = SmsCampaign.builder()
                .restaurantId(requireWritableTenant())
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

        // Build recipient records. CUSTOM is an explicit phone list (numbers need not belong to a
        // customer); every other audience resolves to Customer rows via buildRecipientList.
        List<SmsCampaignRecipient> recipients = campaign.getTargetAudience() == TargetAudience.CUSTOM
                ? buildCustomRecipientRecords(campaign)
                : toCustomerRecipientRecords(campaign, buildRecipientList(campaign));
        campaign.setRecipientCount(recipients.size());
        campaign = campaignRepository.save(campaign);

        recipientRepository.saveAll(recipients);

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
        requireSupportedAudience(request.getTargetAudience());
        requireValidSegmentCriteria(request.getTargetAudience(), request.getFilterCriteria());
        requireValidCustomCriteria(request.getTargetAudience(), request.getFilterCriteria());

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
                log.error("Failed to send SMS to {}: {}", LogSanitizer.phone(recipient.getPhone()), e.getMessage());
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

    /**
     * Target audiences whose recipient list is actually implemented.
     * Everything else is rejected up-front by {@link #requireSupportedAudience}:
     *   - Customer-query audiences (ALL / BIRTHDAY_TODAY / INACTIVE / NEW_CUSTOMERS) resolve to
     *     Customer rows via {@link #buildRecipientList}.
     *   - SEGMENT resolves against {@code filterCriteria}: a customer tag ({@code {"tag":"vip"}}) or a
     *     computed RFM bucket ({@code {"rfm_segment":"Champions"}}); {@link #requireValidSegmentCriteria}
     *     enforces exactly one and a known RFM name so it can never silently target nobody.
     *   - CUSTOM is an explicit phone list ({@code {"phones":["+998..."]}}) built by
     *     {@link #buildCustomRecipientRecords}; {@link #requireValidCustomCriteria} requires at least
     *     one well-formed number.
     *   - LOYAL_CUSTOMERS / HIGH_VALUE were never added to the switch, so they fell through to the
     *     default branch and blasted every active customer.
     * FUNC-8: fail honestly instead of faking delivery or spamming the whole customer base.
     */
    private static final Set<TargetAudience> SUPPORTED_AUDIENCES = EnumSet.of(
            TargetAudience.ALL,
            TargetAudience.BIRTHDAY_TODAY,
            TargetAudience.INACTIVE,
            TargetAudience.NEW_CUSTOMERS,
            TargetAudience.SEGMENT,
            TargetAudience.CUSTOM);

    /** E.164-ish phone shape, mirrors SelfServiceOrderService: optional +, then 7-20 digits/space/dash/paren. */
    private static final Pattern CUSTOM_PHONE_PATTERN = Pattern.compile("^\\+?[\\d\\s\\-()]{7,20}$");

    private void requireSupportedAudience(TargetAudience audience) {
        if (audience == null || !SUPPORTED_AUDIENCES.contains(audience)) {
            throw new BadRequestException(
                    "Target audience '" + audience + "' is not supported yet. Supported audiences: "
                            + "ALL, BIRTHDAY_TODAY, INACTIVE, NEW_CUSTOMERS, SEGMENT, CUSTOM.");
        }
    }

    /**
     * CUSTOM campaigns carry an explicit phone list in {@code filterCriteria} as
     * {@code {"phones": ["+998...", ...]}}. At least one well-formed number is required, and every
     * listed number must be valid — otherwise the campaign would build an empty (or partly bogus)
     * recipient list. No-op for every other audience.
     */
    private void requireValidCustomCriteria(TargetAudience audience, Map<String, Object> filterCriteria) {
        if (audience != TargetAudience.CUSTOM) {
            return;
        }
        List<String> phones = customPhones(filterCriteria);
        if (phones.isEmpty()) {
            throw new BadRequestException(
                    "CUSTOM campaigns require at least one phone number in filterCriteria.phones.");
        }
        for (String phone : phones) {
            if (!CUSTOM_PHONE_PATTERN.matcher(phone).matches()) {
                throw new BadRequestException("Invalid phone number '" + phone + "' in CUSTOM campaign.");
            }
        }
    }

    /**
     * SEGMENT campaigns target either a customer tag or a computed RFM bucket, carried in
     * {@code filterCriteria} as {@code {"tag": "..."}} or {@code {"rfm_segment": "..."}}. Exactly one
     * must be present (both/neither is a mistake), and an RFM bucket must be a known label — otherwise
     * the campaign would build an empty recipient list and quietly send to nobody (FUNC-8). No-op for
     * every other audience.
     */
    private void requireValidSegmentCriteria(TargetAudience audience, Map<String, Object> filterCriteria) {
        if (audience != TargetAudience.SEGMENT) {
            return;
        }
        String tag = segmentCriterion(filterCriteria, "tag");
        String rfmSegment = segmentCriterion(filterCriteria, "rfm_segment");
        boolean hasTag = tag != null;
        boolean hasRfm = rfmSegment != null;

        if (hasTag == hasRfm) {
            throw new BadRequestException(
                    "SEGMENT campaigns require exactly one of filterCriteria.tag or "
                            + "filterCriteria.rfm_segment.");
        }
        if (hasRfm && !isKnownRfmSegment(rfmSegment)) {
            throw new BadRequestException(
                    "Unknown RFM segment '" + rfmSegment + "'. Known segments: "
                            + String.join(", ", CustomerActivityService.RFM_SEGMENTS) + ".");
        }
    }

    /** Read a String criterion from filterCriteria, returning null when absent or blank. */
    private String segmentCriterion(Map<String, Object> filterCriteria, String key) {
        if (filterCriteria == null) {
            return null;
        }
        Object value = filterCriteria.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isKnownRfmSegment(String rfmSegment) {
        return CustomerActivityService.RFM_SEGMENTS.stream()
                .anyMatch(known -> known.equalsIgnoreCase(rfmSegment));
    }

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
                return resolveSegmentRecipients(campaign);
            default:
                // CUSTOM is routed to its phone-list builder before this switch; LOYAL_CUSTOMERS /
                // HIGH_VALUE are rejected up-front by requireSupportedAudience(). Reaching here means a
                // guard was bypassed — fail loudly instead of blasting every active customer.
                throw new IllegalStateException(
                        "Unsupported target audience reached recipient build: " + campaign.getTargetAudience());
        }
    }

    /**
     * Resolve SEGMENT recipients from the campaign's filterCriteria (validated at create/update by
     * {@link #requireValidSegmentCriteria}):
     *   - {@code tag}: active customers whose tags contain the token (case-insensitive, space-tolerant).
     *   - {@code rfm_segment}: active customers whose computed RFM bucket matches, via the RFM engine.
     * A known criterion that currently matches nobody honestly yields an empty list (recipientCount 0),
     * which is surfaced to the operator — unlike the old silent no-op.
     */
    private List<Customer> resolveSegmentRecipients(SmsCampaign campaign) {
        Map<String, Object> filterCriteria = campaign.getFilterCriteria();
        String tag = segmentCriterion(filterCriteria, "tag");
        if (tag != null) {
            String token = tag.toLowerCase().replace(" ", "");
            return customerRepository.findActiveByTagToken(token);
        }

        String rfmSegment = segmentCriterion(filterCriteria, "rfm_segment");
        if (rfmSegment != null) {
            Set<Long> matchedIds = customerActivityService.getAllCustomersActivity().stream()
                    .filter(activity -> rfmSegment.equalsIgnoreCase(activity.getRfmSegment()))
                    .map(CustomerActivityDTO::getCustomerId)
                    .collect(Collectors.toSet());
            if (matchedIds.isEmpty()) {
                return List.of();
            }
            return customerRepository.findAllById(matchedIds);
        }

        // requireValidSegmentCriteria guarantees one of the above; reaching here means it was bypassed.
        throw new IllegalStateException(
                "SEGMENT campaign " + campaign.getId() + " has no tag/rfm_segment criterion");
    }

    /** Build recipient rows from resolved customers (skips those without a phone). Caller persists. */
    private List<SmsCampaignRecipient> toCustomerRecipientRecords(SmsCampaign campaign, List<Customer> customers) {
        return customers.stream()
                .filter(c -> c.getPhone() != null && !c.getPhone().isEmpty())
                .map(c -> SmsCampaignRecipient.builder()
                        // Always the campaign's own tenant.
                        .restaurantId(campaign.getRestaurantId())
                        .campaign(campaign)
                        .customerId(c.getId())
                        .phone(c.getPhone())
                        .customerName(c.getFirstName() + " " + c.getLastName())
                        .status(MessageStatus.PENDING)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Build recipient rows for a CUSTOM campaign from its explicit phone list (validated at
     * create/update by {@link #requireValidCustomCriteria}). Each number is enriched with the matching
     * customer's id/name when one exists (so {name} personalization still works), otherwise it is sent
     * as a bare number. Caller persists.
     */
    private List<SmsCampaignRecipient> buildCustomRecipientRecords(SmsCampaign campaign) {
        return customPhones(campaign.getFilterCriteria()).stream()
                .map(phone -> {
                    Optional<Customer> match = customerRepository.findFirstByPhoneOrderByIdAsc(phone);
                    return SmsCampaignRecipient.builder()
                            .restaurantId(campaign.getRestaurantId())
                            .campaign(campaign)
                            .customerId(match.map(Customer::getId).orElse(null))
                            .phone(phone)
                            .customerName(match.map(c -> c.getFirstName() + " " + c.getLastName()).orElse(null))
                            .status(MessageStatus.PENDING)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Extract the CUSTOM phone list from filterCriteria.phones: trim each entry, drop blanks and
     * duplicates, preserve order. Returns an empty list when the key is absent or not a collection
     * (format is validated separately by {@link #requireValidCustomCriteria}).
     */
    private List<String> customPhones(Map<String, Object> filterCriteria) {
        if (filterCriteria == null) {
            return List.of();
        }
        Object raw = filterCriteria.get("phones");
        if (!(raw instanceof Collection<?> values)) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (Object value : values) {
            if (value != null) {
                String phone = value.toString().trim();
                if (!phone.isEmpty()) {
                    unique.add(phone);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private String personalizeMessage(String template, SmsCampaignRecipient recipient) {
        if (template == null) return "";
        return template
                .replace("{name}", recipient.getCustomerName() != null ? recipient.getCustomerName() : "")
                .replace("{phone}", recipient.getPhone() != null ? recipient.getPhone() : "");
    }

    private void createSmsLog(SmsCampaignRecipient recipient, SmsCampaign campaign, SmsMessageType type) {
        SmsLog log = SmsLog.builder()
                .restaurantId(campaign.getRestaurantId())
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

    /**
     * V165: SMS marketing data belongs to a restaurant — a campaign may only ever target its own
     * customers. A platform account has no customer base of its own to message.
     */
    private Long requireWritableTenant() {
        Long restaurantId = restaurantAuthorizationService.currentTenantScopeStrict();
        if (restaurantId == null) {
            throw new BadRequestException(
                    "An SMS campaign belongs to a restaurant. Sign in with a restaurant-scoped account "
                            + "to create one.");
        }
        return restaurantId;
    }
}
